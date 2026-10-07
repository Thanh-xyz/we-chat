package main.com.chat.wechat.common.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Redis-backed intervally refilled bucket. The Lua script reads Redis server
 * time and performs read, refill, consume, and TTL update atomically.
 */
public class RedisRateLimiter implements RateLimiter {
	private static final Logger LOGGER = LoggerFactory.getLogger(RedisRateLimiter.class);
	private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
			local raw = redis.call('GET', KEYS[1])
			local time = redis.call('TIME')
			local now = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)
			local capacity = tonumber(ARGV[1])
			local interval = tonumber(ARGV[2])
			local tokens = capacity
			local refillAt = now + interval

			if raw then
				local separator = string.find(raw, '|', 1, true)
				if separator then
					tokens = tonumber(string.sub(raw, 1, separator - 1)) or capacity
					refillAt = tonumber(string.sub(raw, separator + 1)) or (now + interval)
				end
			end

			if now >= refillAt then
				tokens = capacity
				refillAt = now + interval
			end

			local allowed = 0
			if tokens > 0 then
				tokens = tokens - 1
				allowed = 1
			end

			local ttl = refillAt - now
			if ttl < 1 then ttl = 1 end
			if ttl > interval then ttl = interval end
			redis.call('SET', KEYS[1], tostring(tokens) .. '|' .. tostring(refillAt), 'PX', ttl)
			return allowed
			""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final RateLimitProperties properties;
	private final InMemoryRateLimiter fallback;
	private final RateLimitMetrics metrics;

	public RedisRateLimiter(
			StringRedisTemplate redisTemplate,
			RateLimitProperties properties,
			MeterRegistry meterRegistry) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.metrics = new RateLimitMetrics(meterRegistry);
		this.fallback = new InMemoryRateLimiter(properties.fallbackMaxEntries(), meterRegistry);
	}

	@Override
	public boolean tryConsume(
			String operation,
			RateLimitKey key,
			RateLimitProperties.Limit limit) {
		String redisKey = redisKey(operation, key);
		long intervalMillis = intervalMillis(limit);
		try {
			Long allowed = redisTemplate.execute(
					CONSUME_SCRIPT,
					List.of(redisKey),
					Integer.toString(limit.capacity()),
					Long.toString(intervalMillis));
			if (allowed == null) {
				throw new IllegalStateException("Redis rate-limit script returned no decision");
			}
			boolean result = allowed == 1L;
			metrics.decision(operation, result, "redis");
			return result;
		} catch (RuntimeException exception) {
			metrics.error(operation, "redis_error");
			metrics.redisFailure(operation);
			metrics.fallback(operation);
			LOGGER.warn(
					"Redis rate limit unavailable operation={} fallback=bounded_local exceptionClass={}",
					operation,
					exception.getClass().getSimpleName());
			return fallback.tryConsume(operation, key, properties.fallbackLimit());
		}
	}

	String redisKey(String operation, RateLimitKey key) {
		if (operation == null || !operation.matches("[A-Za-z0-9._-]{1,64}")) {
			throw new IllegalArgumentException("Rate-limit operation contains unsafe characters");
		}
		return properties.namespace() + ":" + properties.environment() + ":" + operation + ":" + key.value();
	}

	private long intervalMillis(RateLimitProperties.Limit limit) {
		try {
			return Math.max(1_000L, Math.multiplyExact(limit.refillMinutes(), 60_000L));
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}
}
