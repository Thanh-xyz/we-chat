package main.com.chat.wechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(properties = "app.jwt.secret=test-context-secret-test-context-secret-1234")
@ActiveProfiles("test")
@EnabledIf("databaseConfigurationAvailable")
class WechatApplicationTests {

	@Test
	void contextLoads() {
	}

	static boolean databaseConfigurationAvailable() {
		return "true".equalsIgnoreCase(System.getenv("RUN_DB_TESTS"))
				&& (System.getenv("DATABASE_URL") != null
				|| Files.exists(Path.of(".env"))
				|| Files.exists(Path.of("../.env"))
				|| Files.exists(Path.of("wechatbackend/.env")));
	}

}
