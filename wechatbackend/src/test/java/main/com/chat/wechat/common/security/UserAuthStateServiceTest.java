package main.com.chat.wechat.common.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthStateServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private UserRepository userRepository;

	@Mock
	private AuthStateCache cache;

	private UserAuthStateService service;

	@BeforeEach
	void setUp() {
		service = new UserAuthStateService(userRepository, cache, new SimpleMeterRegistry());
	}

	@Test
	void cacheHitAvoidsAuthoritativeDatabaseLookup() {
		AuthState state = new AuthState(USER_ID, 3, true);
		when(cache.lookup(USER_ID)).thenReturn(new AuthStateCache.CacheLookup(AuthStateCache.Status.HIT, Optional.of(state)));

		assertThat(service.find(USER_ID)).contains(state);

		verify(userRepository, never()).findById(USER_ID);
	}

	@Test
	void cacheMissReadsMinimalStateFromDatabaseAndPopulatesCache() {
		User user = user("ACTIVE", null, 4);
		when(cache.lookup(USER_ID)).thenReturn(new AuthStateCache.CacheLookup(AuthStateCache.Status.MISS, Optional.empty()));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

		assertThat(service.find(USER_ID)).contains(new AuthState(USER_ID, 4, true));

		verify(cache).put(new AuthState(USER_ID, 4, true));
	}

	@Test
	void redisErrorsFallBackToDatabaseWithoutFailingOpen() {
		User user = user("DISABLED", Instant.parse("2026-06-05T07:00:00Z"), 7);
		when(cache.lookup(USER_ID)).thenReturn(new AuthStateCache.CacheLookup(AuthStateCache.Status.REDIS_ERROR, Optional.empty()));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

		assertThat(service.find(USER_ID)).contains(new AuthState(USER_ID, 7, false));
		verify(cache).put(new AuthState(USER_ID, 7, false));
	}

	private User user(String accountStatus, Instant deletedAt, int tokenVersion) {
		Instant now = Instant.parse("2026-06-05T07:00:00Z");
		return new User(USER_ID, "user", "user@example.com", "hash", "User", null,
				"OFFLINE", "USER", true, accountStatus, true, tokenVersion, null, 0, null,
				deletedAt, now, now);
	}
}
