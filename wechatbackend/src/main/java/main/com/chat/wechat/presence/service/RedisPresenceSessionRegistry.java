package main.com.chat.wechat.presence.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cluster-wide registry: each user has a Redis sorted set of session ids scored by expiry time.
 * Instances refresh their own sessions on a heartbeat, so a crashed instance's sessions expire.
 */
@Component
@ConditionalOnProperty(name = "app.realtime.distributed-enabled", havingValue = "true")
public class RedisPresenceSessionRegistry implements PresenceSessionRegistry {
	static final Duration SESSION_TTL = Duration.ofSeconds(90);
	private static final String KEY_PREFIX = "webchat:presence:sessions:";

	private final StringRedisTemplate redisTemplate;
	private final Clock clock;
	private final Map<UUID, Set<String>> localSessions = new ConcurrentHashMap<>();

	public RedisPresenceSessionRegistry(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
		this.clock = Clock.systemUTC();
	}

	@Override
	public int connect(UUID userId, String sessionId) {
		localSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
		touch(userId, sessionId);
		return liveCount(userId);
	}

	@Override
	public int disconnect(UUID userId, String sessionId) {
		localSessions.computeIfPresent(userId, (ignored, sessions) -> {
			sessions.remove(sessionId);
			return sessions.isEmpty() ? null : sessions;
		});
		redisTemplate.opsForZSet().remove(key(userId), sessionId);
		return liveCount(userId);
	}

	@Override
	public boolean isOnline(UUID userId) {
		return liveCount(userId) > 0;
	}

	@Override
	public void heartbeat() {
		localSessions.forEach((userId, sessions) -> sessions.forEach(sessionId -> touch(userId, sessionId)));
	}

	private void touch(UUID userId, String sessionId) {
		String key = key(userId);
		redisTemplate.opsForZSet().add(key, sessionId, clock.millis() + SESSION_TTL.toMillis());
		redisTemplate.expire(key, SESSION_TTL.multipliedBy(2));
	}

	private int liveCount(UUID userId) {
		String key = key(userId);
		redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, clock.millis());
		Long count = redisTemplate.opsForZSet().zCard(key);
		return count == null ? 0 : count.intValue();
	}

	private static String key(UUID userId) {
		return KEY_PREFIX + userId;
	}
}
