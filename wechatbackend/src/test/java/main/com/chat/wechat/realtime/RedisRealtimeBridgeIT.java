package main.com.chat.wechat.realtime;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import main.com.chat.wechat.realtime.config.RealtimeProperties;
import main.com.chat.wechat.realtime.service.LocalRealtimeDelivery;
import main.com.chat.wechat.realtime.service.RedisRealtimeBroker;
import main.com.chat.wechat.realtime.service.RedisRealtimeListener;
import main.com.chat.wechat.realtime.service.RealtimeDestinationPolicy;
import main.com.chat.wechat.realtime.service.RealtimeEnvelopeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Testcontainers(disabledWithoutDocker = true)
class RedisRealtimeBridgeIT {
	private static final String PASSWORD = "redis-it-password";
	private static final String CHANNEL = "webchat:test:realtime";

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(
			DockerImageName.parse("redis:7.4.2-alpine3.21"))
			.withCommand("redis-server", "--requirepass", PASSWORD, "--appendonly", "no", "--save", "")
			.withExposedPorts(6379)
			.withStartupTimeout(Duration.ofMinutes(2));

	private LettuceConnectionFactory connectionFactoryA;
	private LettuceConnectionFactory connectionFactoryB;
	private RedisMessageListenerContainer containerA;
	private RedisMessageListenerContainer containerB;

	@Test
	void publishesBetweenTwoRedisSubscribersAndDeduplicatesByEventId() throws Exception {
		RealtimeDestinationPolicy policy = new RealtimeDestinationPolicy();
		RealtimeProperties propertiesA = properties("instance-a");
		RealtimeProperties propertiesB = properties("instance-b");
		RealtimeEnvelopeFactory factoryA = new RealtimeEnvelopeFactory(new tools.jackson.databind.ObjectMapper(), propertiesA, policy);
		RealtimeEnvelopeFactory factoryB = new RealtimeEnvelopeFactory(new tools.jackson.databind.ObjectMapper(), propertiesB, policy);
		StringRedisTemplate templateA = template(propertiesA);
		StringRedisTemplate templateB = template(propertiesB);

		LocalRealtimeDelivery deliveryA = Mockito.mock(LocalRealtimeDelivery.class);
		LocalRealtimeDelivery deliveryB = Mockito.mock(LocalRealtimeDelivery.class);
		RedisRealtimeListener listenerA = new RedisRealtimeListener(
				propertiesA, factoryA, policy, deliveryA, new SimpleMeterRegistry());
		RedisRealtimeListener listenerB = new RedisRealtimeListener(
				propertiesB, factoryB, policy, deliveryB, new SimpleMeterRegistry());
		containerA = container(listenerA, propertiesA);
		containerB = container(listenerB, propertiesB);
		containerA.start();
		containerB.start();
		Thread.sleep(500);

		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000004");
		CountDownLatch bridgeLatch = new CountDownLatch(2);
		doAnswer(invocation -> {
			bridgeLatch.countDown();
			return null;
		}).when(deliveryA).deliver(any());
		doAnswer(invocation -> {
			bridgeLatch.countDown();
			return null;
		}).when(deliveryB).deliver(any());

		RedisRealtimeBroker brokerA = new RedisRealtimeBroker(
				templateA, propertiesA, factoryA, new SyncTaskExecutor(), new SimpleMeterRegistry());
		brokerA.publish(policy.userTopic(userId), "message.created", Map.of("messageId", "m1"));

		assertThat(bridgeLatch.await(10, TimeUnit.SECONDS)).isTrue();
		verify(deliveryA).deliver(any());
		verify(deliveryB).deliver(any());

		clearInvocations(deliveryA, deliveryB);
		var duplicate = factoryA.create(policy.userTopic(userId), "message.created", Map.of("messageId", "m2"));
		String duplicateBody = new String(factoryA.serialize(duplicate), java.nio.charset.StandardCharsets.UTF_8);
		CountDownLatch duplicateLatch = new CountDownLatch(2);
		doAnswer(invocation -> {
			duplicateLatch.countDown();
			return null;
		}).when(deliveryA).deliver(any());
		doAnswer(invocation -> {
			duplicateLatch.countDown();
			return null;
		}).when(deliveryB).deliver(any());
		templateA.convertAndSend(CHANNEL, duplicateBody);
		assertThat(duplicateLatch.await(5, TimeUnit.SECONDS)).isTrue();
		clearInvocations(deliveryA, deliveryB);
		templateA.convertAndSend(CHANNEL, duplicateBody);
		Thread.sleep(500);
		verifyNoInteractions(deliveryA, deliveryB);

		templateB.convertAndSend(CHANNEL, "{\"version\":999,\"payload\":null}");
		Thread.sleep(300);
		verifyNoInteractions(deliveryA, deliveryB);
	}

	private StringRedisTemplate template(RealtimeProperties properties) {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(
				redis.getHost(), redis.getMappedPort(6379));
		factory.setPassword(PASSWORD);
		factory.afterPropertiesSet();
		if (properties.instanceId().equals("instance-a")) {
			connectionFactoryA = factory;
		} else {
			connectionFactoryB = factory;
		}
		StringRedisTemplate template = new StringRedisTemplate(factory);
		template.afterPropertiesSet();
		return template;
	}

	private RedisMessageListenerContainer container(
			RedisRealtimeListener listener,
			RealtimeProperties properties) {
		LettuceConnectionFactory factory = properties.instanceId().equals("instance-a")
				? connectionFactoryA : connectionFactoryB;
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(factory);
		container.setTaskExecutor(new SyncTaskExecutor());
		container.addMessageListener(listener, new ChannelTopic(CHANNEL));
		container.afterPropertiesSet();
		return container;
	}

	@AfterEach
	void stopResources() {
		if (containerA != null) {
			containerA.stop();
		}
		if (containerB != null) {
			containerB.stop();
		}
		if (connectionFactoryA != null) {
			connectionFactoryA.destroy();
		}
		if (connectionFactoryB != null) {
			connectionFactoryB.destroy();
		}
	}

	private RealtimeProperties properties(String instanceId) {
		return new RealtimeProperties(
				true, CHANNEL, instanceId, 262144,
				1, 2, 100, 1, 2, 100, 1000, 10);
	}
}
