package main.com.chat.wechat.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {
	private static final String VALID_SECRET = "test-context-secret-test-context-secret-1234";
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(JwtPropertiesConfiguration.class);

	@Test
	void validSecretLoadsConfiguration() {
		contextRunner
				.withPropertyValues(
						"app.jwt.secret=" + VALID_SECRET,
						"app.jwt.issuer=wechat-test",
						"app.jwt.access-token-ttl=PT15M",
						"app.jwt.refresh-token-ttl=P30D")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(JwtProperties.class);
					assertThat(context.getBean(JwtProperties.class).secret()).isEqualTo(VALID_SECRET);
				});
	}

	@Test
	void missingSecretFailsConfigurationStartup() {
		contextRunner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void blankSecretFailsConfigurationStartup() {
		contextRunner
				.withPropertyValues("app.jwt.secret=   ")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void shortSecretFailsConfigurationStartup() {
		contextRunner
				.withPropertyValues("app.jwt.secret=too-short")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void knownDevelopmentSecretFailsConfigurationStartup() {
		contextRunner
				.withPropertyValues("app.jwt.secret=" + knownDevelopmentSecret())
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void directValidationRejectsBlankAndShortSecrets() {
		assertThatThrownBy(() -> new JwtProperties(" ", "wechat-test", Duration.ofMinutes(15), Duration.ofDays(30)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JwtProperties("short", "wechat-test", Duration.ofMinutes(15), Duration.ofDays(30)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private String knownDevelopmentSecret() {
		return String.join("-", "change", "this", "dev", "secret", "change", "this", "dev", "secret");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(JwtProperties.class)
	static class JwtPropertiesConfiguration {
	}
}
