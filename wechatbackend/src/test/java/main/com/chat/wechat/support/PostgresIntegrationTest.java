package main.com.chat.wechat.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
		"app.jwt.secret=postgres-integration-secret-at-least-32-bytes",
		"spring.config.import=",
		"app.notification.executor.shutdown-timeout=PT1S"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {
	@Container
	@ServiceConnection
	protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
			.withEnv("TZ", "UTC");

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void resetPostgresState() {
		jdbc.execute("truncate table users restart identity cascade");
	}
}
