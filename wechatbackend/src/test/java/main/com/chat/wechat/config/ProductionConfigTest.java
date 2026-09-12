package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigTest {
	@Test
	void defaultProfileIsProductionSafe() throws Exception {
		Properties defaultConfig = load("src/main/resources/application.properties");

		assertThat(defaultConfig.getProperty("server.error.include-stacktrace")).isEqualTo("never");
		assertThat(defaultConfig.getProperty("server.error.include-message")).isEqualTo("never");
		assertThat(defaultConfig.getProperty("server.error.include-binding-errors")).isEqualTo("never");
		assertThat(defaultConfig.getProperty("server.forward-headers-strategy")).isEqualTo("none");
		assertThat(defaultConfig.getProperty("logging.level.main.com.chat.wechat")).isEqualTo("INFO");
		assertThat(defaultConfig.getProperty("logging.level.org.springframework.boot")).isEqualTo("INFO");
		assertThat(defaultConfig.getProperty("logging.level.org.springframework.security")).isEqualTo("WARN");
		assertThat(defaultConfig.getProperty("logging.level.org.springframework.web")).isEqualTo("INFO");
		assertThat(defaultConfig.getProperty("logging.level.org.springframework.messaging")).isEqualTo("INFO");
		assertThat(defaultConfig.getProperty("logging.level.org.springframework.jdbc.core")).isEqualTo("INFO");
		assertThat(defaultConfig.getProperty("logging.level.org.springframework.jdbc.core.StatementCreatorUtils")).isEqualTo("OFF");
		assertThat(defaultConfig.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("OFF");
		assertThat(defaultConfig.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("OFF");
		assertThat(defaultConfig.getProperty("app.cors.allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS:}");
		assertThat(defaultConfig.getProperty("app.cors.allowed-methods"))
				.isEqualTo("${CORS_ALLOWED_METHODS:GET,POST,PUT,PATCH,DELETE,OPTIONS}");
		assertThat(defaultConfig.getProperty("app.cors.allowed-headers"))
				.isEqualTo("${CORS_ALLOWED_HEADERS:Authorization,Content-Type,Accept,Origin,X-Request-ID}");
		assertThat(defaultConfig.getProperty("app.cors.exposed-headers")).isEqualTo("${CORS_EXPOSED_HEADERS:X-Request-ID}");
		assertThat(defaultConfig.getProperty("app.auth.mail.host")).isEqualTo("${MAIL_HOST:}");
		assertThat(defaultConfig.getProperty("app.auth.mail.port")).isEqualTo("${MAIL_PORT:587}");
		assertThat(defaultConfig.getProperty("app.auth.mail.username")).isEqualTo("${MAIL_USERNAME:}");
		assertThat(defaultConfig.getProperty("app.auth.mail.password")).isEqualTo("${MAIL_PASSWORD:}");
		assertThat(defaultConfig.getProperty("app.auth.mail.from")).isEqualTo("${MAIL_FROM:}");
	}

	@Test
	void localProfileKeepsSqlDiagnosticsWithoutBindValues() throws Exception {
		Properties localConfig = load("src/main/resources/application-local.properties");

		assertThat(localConfig.getProperty("logging.level.main.com.chat.wechat")).isEqualTo("DEBUG");
		assertThat(localConfig.getProperty("logging.level.org.springframework.web")).isEqualTo("INFO");
		assertThat(localConfig.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("DEBUG");
		assertThat(localConfig.getProperty("logging.level.org.springframework.jdbc.core.JdbcTemplate")).isEqualTo("DEBUG");
		assertThat(localConfig.getProperty("logging.level.org.springframework.jdbc.core.StatementCreatorUtils")).isEqualTo("OFF");
		assertThat(localConfig.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("OFF");
		assertThat(localConfig.getProperty("server.error.include-stacktrace")).isEqualTo("on_param");
		assertThat(localConfig).doesNotContainKey("server.forward-headers-strategy");
		assertThat(localConfig.getProperty("app.auth.email-verification-url"))
				.isEqualTo("${EMAIL_VERIFICATION_URL:http://localhost:5173/verify-email?token=}");
		assertThat(localConfig.getProperty("app.auth.password-reset-url"))
				.isEqualTo("${PASSWORD_RESET_URL:http://localhost:5173/reset-password?token=}");
		assertThat(localConfig.getProperty("app.cors.allowed-origins"))
				.isEqualTo("http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000");
	}

	@Test
	void prodProfileDoesNotExposeErrorsOrSqlValues() throws Exception {
		Properties prodConfig = load("src/main/resources/application-prod.properties");

		assertThat(prodConfig.getProperty("server.error.include-stacktrace")).isEqualTo("never");
		assertThat(prodConfig.getProperty("server.error.include-message")).isEqualTo("never");
		assertThat(prodConfig.getProperty("server.error.include-binding-errors")).isEqualTo("never");
		assertThat(prodConfig.getProperty("server.forward-headers-strategy")).isEqualTo("native");
		assertThat(prodConfig.getProperty("server.tomcat.remoteip.remote-ip-header")).isEqualTo("X-Forwarded-For");
		assertThat(prodConfig.getProperty("server.tomcat.remoteip.internal-proxies"))
				.isEqualTo("${TRUSTED_PROXY_CIDRS:}");
		assertThat(prodConfig.getProperty("server.tomcat.remoteip.trusted-proxies")).isEmpty();
		assertThat(prodConfig.getProperty("app.cors.allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS:}");
		assertThat(prodConfig.getProperty("app.cors.allowed-origins")).doesNotContain("*");
		assertThat(prodConfig.getProperty("logging.level.org.springframework.boot")).isEqualTo("INFO");
		assertThat(prodConfig.getProperty("logging.level.org.springframework.web")).isEqualTo("INFO");
		assertThat(prodConfig.getProperty("logging.level.org.springframework.messaging")).isEqualTo("INFO");
		assertThat(prodConfig.getProperty("logging.level.org.springframework.jdbc.core")).isEqualTo("INFO");
		assertThat(prodConfig.getProperty("logging.level.org.springframework.jdbc.core.StatementCreatorUtils")).isEqualTo("OFF");
		assertThat(prodConfig.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("OFF");
		assertThat(prodConfig.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("OFF");
	}

	@Test
	void emptyTrustedProxySettingOverridesTomcatPrivateNetworkDefaults() {
		MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
				"server.tomcat.remoteip.internal-proxies", "",
				"server.tomcat.remoteip.trusted-proxies", ""));

		TomcatServerProperties properties = new Binder(source)
				.bind("server.tomcat", Bindable.of(TomcatServerProperties.class))
				.orElseThrow(() -> new AssertionError("Tomcat properties were not bound"));

		assertThat(properties.getRemoteip().getInternalProxies()).isEmpty();
		assertThat(properties.getRemoteip().getTrustedProxies()).isEmpty();
	}

	private Properties load(String path) throws IOException {
		Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(Path.of(path))) {
			properties.load(reader);
		}
		return properties;
	}
}
