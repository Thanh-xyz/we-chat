package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusConfigTest {
	@Test
	void scrapeConfigTargetsOnlyTheBackendMetricsEndpointWithoutCredentials() throws IOException {
		Path configPath = Path.of("../monitoring/prometheus/prometheus.yml");
		String config = Files.readString(configPath);

		assertThat(config).contains(
				"job_name: wechat-backend",
				"metrics_path: /actuator/prometheus",
				"localhost:8080");
		assertThat(config).doesNotContain("password", "username", "secret", "token", "Authorization:");
	}
}
