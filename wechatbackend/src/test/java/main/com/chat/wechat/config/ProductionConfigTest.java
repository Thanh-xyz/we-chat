package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigTest {
	@Test
	void prodProfileDoesNotExposeStacktraceOrDebugSql() throws Exception {
		String prodConfig = Files.readString(Path.of("src/main/resources/application-prod.properties"));

		assertThat(prodConfig).contains("server.error.include-stacktrace=never");
		assertThat(prodConfig).contains("server.error.include-message=never");
		assertThat(prodConfig).doesNotContain("server.error.include-stacktrace=always");
		assertThat(prodConfig).doesNotContain("logging.level.org.springframework.security=DEBUG");
		assertThat(prodConfig).doesNotContain("logging.level.org.hibernate.orm.jdbc.bind=TRACE");
	}

	@Test
	void localProfileKeepsDeveloperDiagnosticsEnabled() throws Exception {
		String localConfig = Files.readString(Path.of("src/main/resources/application-local.properties"));

		assertThat(localConfig).contains("logging.level.org.springframework.security=DEBUG");
		assertThat(localConfig).contains("server.error.include-stacktrace=always");
	}
}
