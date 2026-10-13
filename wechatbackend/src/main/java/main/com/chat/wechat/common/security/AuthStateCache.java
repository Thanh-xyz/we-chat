package main.com.chat.wechat.common.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuthStateCache {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthStateCache.class);

	private final StringRedisTemplate redisTemplate;
	private final AuthStateProperties properties;
	private final ObjectMapper objectMapper;
	private final Map<Status, Counter> lookupCounters;
	private final Counter cacheWrites;
	private final Counter cacheInvalidations;
	private final Counter cacheErrors;
	private final Counter invalidationErrors;
	private final Counter malformedValues;

	public AuthStateCache(
			StringRedisTemplate redisTemplate,
			AuthStateProperties properties,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.lookupCounters = new EnumMap<>(Status.class);
		for (Status status : Status.values()) {
			this.lookupCounters.put(status, Counter.builder("auth_state_cache_lookups")
					.tag("result", status.name().toLowerCase(Locale.ROOT))
					.description("Authentication state cache lookups by bounded result")
					.register(meterRegistry));
		}
		this.cacheWrites = Counter.builder("auth_state_cache_writes")
				.description("Authentication state cache writes")
				.register(meterRegistry);
		this.cacheInvalidations = Counter.builder("auth_state_cache_invalidations")
				.description("Authentication state cache invalidations")
				.register(meterRegistry);
		this.cacheErrors = Counter.builder("auth_state_cache_errors")
				.description("Authentication state cache Redis errors")
				.register(meterRegistry);
		this.invalidationErrors = Counter.builder("auth_state_cache_invalidation_errors")
				.description("Authentication state cache invalidation failures")
				.register(meterRegistry);
		this.malformedValues = Counter.builder("auth_state_cache_malformed_values")
				.description("Malformed authentication state cache values")
				.register(meterRegistry);
	}

	public CacheLookup lookup(UUID userId) {
		Objects.requireNonNull(userId, "userId");
		if (!properties.enabled()) {
			recordLookup(Status.DISABLED);
			return CacheLookup.disabled();
		}

		String key = key(userId);
		String encoded;
		try {
			encoded = redisTemplate.opsForValue().get(key);
		} catch (RuntimeException exception) {
			cacheErrors.increment();
			recordLookup(Status.REDIS_ERROR);
			LOGGER.warn("Authentication state cache read failed exceptionClass={}", exception.getClass().getName());
			return CacheLookup.redisError();
		}

		if (encoded == null) {
			recordLookup(Status.MISS);
			return CacheLookup.miss();
		}

		try {
			AuthState state = objectMapper.readValue(encoded, AuthState.class);
			if (!userId.equals(state.userId())) {
				throw new IllegalArgumentException("Authentication state user mismatch");
			}
			recordLookup(Status.HIT);
			return CacheLookup.hit(state);
		} catch (Exception exception) {
			malformedValues.increment();
			recordLookup(Status.MALFORMED);
			deleteQuietly(key);
			LOGGER.warn("Authentication state cache value rejected exceptionClass={}", exception.getClass().getName());
			return CacheLookup.malformed();
		}
	}

	public void put(AuthState state) {
		Objects.requireNonNull(state, "state");
		if (!properties.enabled()) {
			return;
		}
		try {
			redisTemplate.opsForValue().set(
					key(state.userId()),
					objectMapper.writeValueAsString(state),
					properties.ttl());
			cacheWrites.increment();
		} catch (Exception exception) {
			cacheErrors.increment();
			LOGGER.warn("Authentication state cache write failed exceptionClass={}", exception.getClass().getName());
		}
	}

	public void invalidateAfterCommit(UUID userId) {
		Objects.requireNonNull(userId, "userId");
		if (!properties.enabled()) {
			return;
		}
		Runnable invalidation = () -> invalidateNow(userId);
		if (TransactionSynchronizationManager.isSynchronizationActive()
				&& TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					invalidation.run();
				}
			});
			return;
		}
		invalidation.run();
	}

	public String key(UUID userId) {
		return properties.namespace() + ":" + properties.environment() + ":user:" + userId;
	}

	private void invalidateNow(UUID userId) {
		try {
			redisTemplate.delete(key(userId));
			cacheInvalidations.increment();
		} catch (RuntimeException exception) {
			cacheErrors.increment();
			invalidationErrors.increment();
			LOGGER.warn("Authentication state cache invalidation failed exceptionClass={}", exception.getClass().getName());
		}
	}

	private void recordLookup(Status status) {
		lookupCounters.get(status).increment();
	}

	private void deleteQuietly(String key) {
		try {
			redisTemplate.delete(key);
		} catch (RuntimeException exception) {
			cacheErrors.increment();
		}
	}

	public enum Status {
		HIT,
		MISS,
		REDIS_ERROR,
		MALFORMED,
		DISABLED
	}

	public record CacheLookup(Status status, Optional<AuthState> state) {
		public CacheLookup {
			Objects.requireNonNull(status, "status");
			state = state == null ? Optional.empty() : state;
		}

		private static CacheLookup hit(AuthState state) {
			return new CacheLookup(Status.HIT, Optional.of(state));
		}

		private static CacheLookup miss() {
			return new CacheLookup(Status.MISS, Optional.empty());
		}

		private static CacheLookup redisError() {
			return new CacheLookup(Status.REDIS_ERROR, Optional.empty());
		}

		private static CacheLookup malformed() {
			return new CacheLookup(Status.MALFORMED, Optional.empty());
		}

		private static CacheLookup disabled() {
			return new CacheLookup(Status.DISABLED, Optional.empty());
		}
	}
}
