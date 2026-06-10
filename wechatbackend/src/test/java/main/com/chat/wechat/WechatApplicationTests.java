package main.com.chat.wechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest
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
