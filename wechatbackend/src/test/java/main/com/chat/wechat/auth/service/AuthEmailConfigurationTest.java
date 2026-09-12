package main.com.chat.wechat.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEmailConfigurationTest {
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(EmailConfiguration.class);

	@Test
	void localProfileUsesSafeLoggingEmailService() {
		contextRunner
				.withPropertyValues("spring.profiles.active=local")
				.run(context -> {
					assertThat(context).hasSingleBean(AuthEmailService.class);
					assertThat(context.getBean(AuthEmailService.class)).isInstanceOf(LoggingAuthEmailService.class);
					assertThat(context).doesNotHaveBean(JavaMailSender.class);
				});
	}

	@Test
	void testProfileUsesSafeLoggingEmailServiceUnlessTestOverridesIt() {
		contextRunner
				.withPropertyValues("spring.profiles.active=test")
				.run(context -> {
					assertThat(context).hasSingleBean(AuthEmailService.class);
					assertThat(context.getBean(AuthEmailService.class)).isInstanceOf(LoggingAuthEmailService.class);
					assertThat(context).doesNotHaveBean(JavaMailSender.class);
				});
	}

	@Test
	void productionProfileUsesSmtpAndNeverRegistersLoggingFallback() {
		contextRunner
				.withPropertyValues(validProductionProperties())
				.run(context -> {
					assertThat(context).hasSingleBean(AuthEmailService.class);
					assertThat(context.getBean(AuthEmailService.class)).isInstanceOf(SmtpAuthEmailService.class);
					assertThat(context).hasSingleBean(JavaMailSender.class);
					assertThat(context).doesNotHaveBean(LoggingAuthEmailService.class);
				});
	}

	@Test
	void productionProfileCannotFallBackToLoggingWhenTestIsAlsoActive() {
		String[] properties = validProductionProperties();
		properties[0] = "spring.profiles.active=prod,test";
		contextRunner
				.withPropertyValues(properties)
				.run(context -> {
					assertThat(context).hasSingleBean(AuthEmailService.class);
					assertThat(context.getBean(AuthEmailService.class)).isInstanceOf(SmtpAuthEmailService.class);
					assertThat(context).doesNotHaveBean(LoggingAuthEmailService.class);
				});
	}

	@Test
	void productionProfileFailsFastWhenMailCredentialsAreMissing() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=prod",
						"app.auth.email-verification-url=https://chat.example.com/verify-email?token=",
						"app.auth.password-reset-url=https://chat.example.com/reset-password?token=")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasRootCauseMessage("app.auth.mail.host must be configured");
				});
	}

	private String[] validProductionProperties() {
		return new String[] {
				"spring.profiles.active=prod",
				"app.auth.email-verification-url=https://chat.example.com/verify-email?token=",
				"app.auth.password-reset-url=https://chat.example.com/reset-password?token=",
				"app.auth.mail.host=smtp.example.com",
				"app.auth.mail.port=587",
				"app.auth.mail.username=mailer",
				"app.auth.mail.password=mail-secret",
				"app.auth.mail.from=no-reply@example.com"
		};
	}

	@Configuration(proxyBeanMethods = false)
	@Import({AuthMailConfiguration.class, LoggingAuthEmailService.class})
	static class EmailConfiguration {
	}
}
