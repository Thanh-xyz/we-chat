package main.com.chat.wechat.common.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthStateCacheTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private AuthStateCache cache;

	@BeforeEach
	void setUp() {
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		cache = new AuthStateCache(
				redisTemplate,
				new AuthStateProperties(true, "webchat:auth-state", "test", Duration.ofSeconds(30)),
				new ObjectMapper(),
				new SimpleMeterRegistry());
	}

	@AfterEach
	void clearTransactionSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	void lookupRejectsMalformedValueAndRemovesIt() {
		String key = cache.key(USER_ID);
		when(valueOperations.get(key)).thenReturn("not-json");

		AuthStateCache.CacheLookup lookup = cache.lookup(USER_ID);

		assertThat(lookup.status()).isEqualTo(AuthStateCache.Status.MALFORMED);
		assertThat(lookup.state()).isEmpty();
		verify(redisTemplate).delete(key);
	}

	@Test
	void invalidationIsDeferredUntilTransactionCommit() {
		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);

		cache.invalidateAfterCommit(USER_ID);

		verify(redisTemplate, never()).delete(cache.key(USER_ID));
		TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
		verify(redisTemplate).delete(cache.key(USER_ID));
	}

	@Test
	void rollbackDoesNotInvalidateTheExistingEntry() {
		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);

		cache.invalidateAfterCommit(USER_ID);

		TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
		synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

		verify(redisTemplate, never()).delete(cache.key(USER_ID));
	}

	@Test
	void writesOnlyMinimalStateWithBoundedTtlAndSafeKey() throws Exception {
		AuthState state = new AuthState(USER_ID, 2, true);
		cache.put(state);

		verify(valueOperations).set(
				eq(cache.key(USER_ID)),
				eq(new ObjectMapper().writeValueAsString(state)),
				eq(Duration.ofSeconds(30)));
		assertThat(cache.key(USER_ID)).doesNotContain("Bearer", "Authorization", "password", "presigned");
	}
}
