package main.com.chat.wechat.common.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class AuthStateCacheRedisIT {
	private static final String PASSWORD = "redis-auth-state-it-password";

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(
			DockerImageName.parse("redis:7.4.2-alpine3.21"))
			.withCommand("redis-server", "--requirepass", PASSWORD, "--appendonly", "no", "--save", "")
			.withExposedPorts(6379)
			.withStartupTimeout(Duration.ofMinutes(2));

	private LettuceConnectionFactory factoryA;
	private LettuceConnectionFactory factoryB;

	@Test
	void independentCacheInstancesShareStateAndInvalidateAcrossReplicas() throws InterruptedException {
		AuthStateProperties properties = new AuthStateProperties(true, "webchat:auth-state", "it", Duration.ofSeconds(30));
		StringRedisTemplate templateA = template(true);
		StringRedisTemplate templateB = template(false);
		AuthStateCache cacheA = new AuthStateCache(templateA, properties, new tools.jackson.databind.ObjectMapper(), new SimpleMeterRegistry());
		AuthStateCache cacheB = new AuthStateCache(templateB, properties, new tools.jackson.databind.ObjectMapper(), new SimpleMeterRegistry());
		UserRepository repositoryA = mock(UserRepository.class);
		UserRepository repositoryB = mock(UserRepository.class);
		UserAuthStateService serviceA = new UserAuthStateService(repositoryA, cacheA, new SimpleMeterRegistry());
		UserAuthStateService serviceB = new UserAuthStateService(repositoryB, cacheB, new SimpleMeterRegistry());
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000004");
		AuthState state = new AuthState(userId, 8, true);
		User user = new User(userId, "user", "user@example.com", "hash", "User", null,
				"OFFLINE", "USER", true, "ACTIVE", true, 8, null, 0, null, null,
				java.time.Instant.parse("2026-06-05T07:00:00Z"), java.time.Instant.parse("2026-06-05T07:00:00Z"));
		when(repositoryA.findById(userId)).thenReturn(java.util.Optional.of(user));

		assertThat(serviceA.find(userId)).contains(state);

		assertThat(serviceB.find(userId)).contains(state);
		verify(repositoryB, never()).findById(userId);
		assertThat(templateA.getExpire(cacheA.key(userId), TimeUnit.MILLISECONDS)).isGreaterThan(0L);
		assertThat(cacheA.key(userId)).doesNotContain("Bearer", "Authorization", "RAW_TOKEN_TEST_MARKER", "password");
		templateA.expire(cacheA.key(userId), 1, TimeUnit.SECONDS);
		Thread.sleep(1500);
		when(repositoryB.findById(userId)).thenReturn(java.util.Optional.of(user));
		assertThat(serviceB.find(userId)).contains(state);
		verify(repositoryB).findById(userId);

		cacheA.invalidateAfterCommit(userId);

		assertThat(serviceB.find(userId)).contains(state);
		verify(repositoryB, org.mockito.Mockito.times(2)).findById(userId);
	}

	private StringRedisTemplate template(boolean first) {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
		factory.setPassword(PASSWORD);
		factory.afterPropertiesSet();
		if (first) {
			factoryA = factory;
		} else {
			factoryB = factory;
		}
		StringRedisTemplate template = new StringRedisTemplate(factory);
		template.afterPropertiesSet();
		return template;
	}

	@AfterEach
	void stopResources() {
		if (factoryA != null) {
			factoryA.destroy();
			factoryA = null;
		}
		if (factoryB != null) {
			factoryB.destroy();
			factoryB = null;
		}
	}
}
