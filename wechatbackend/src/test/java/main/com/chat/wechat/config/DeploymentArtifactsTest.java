package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactsTest {
	private static final Path REPOSITORY_ROOT = Path.of("..");

	@Test
	void backendImageIsMultiStageNonRootAndContainsNoConfiguredSecrets() throws IOException {
		String dockerfile = read("docker/backend/Dockerfile");

		assertThat(dockerfile).contains(
				"FROM maven:3.9.16-eclipse-temurin-21-alpine AS build",
				"FROM eclipse-temurin:21-jre-alpine-3.24 AS runtime",
				"COPY --from=build",
				"USER 10001:10001",
				"/actuator/health/liveness",
				"ENTRYPOINT [\"java\", \"-jar\", \"/app/application.jar\"]");
		assertThat(dockerfile).doesNotContain(
				"ENV JWT_SECRET",
				"ENV MAIL_PASSWORD",
				"ENV DATABASE_PASSWORD",
				"COPY . .");
	}

	@Test
	void composeKeepsDatabasePrivateAndValidatesRequiredRuntimeConfiguration() throws IOException {
		String compose = read("docker-compose.yml");
		String postgresService = compose.substring(
				compose.indexOf("  postgres:"),
				compose.indexOf("  backend:"));

		assertThat(compose).contains(
				"image: postgres:17.11-alpine3.24",
				"condition: service_healthy",
				"/actuator/health/readiness",
				"postgres_data:/var/lib/postgresql/data",
				"uploads_data:/app/uploads",
				"internal: true",
				"read_only: true",
				"no-new-privileges:true");
		assertThat(postgresService).doesNotContain("ports:");
		assertThat(compose).contains(
				"${DB_PASSWORD:?",
				"${JWT_SECRET:?",
				"${MAIL_PASSWORD:?",
				"${CORS_ALLOWED_ORIGINS:?",
				"${WEBSOCKET_ALLOWED_ORIGINS:?",
				"${TRUSTED_PROXY_CIDRS:?");
		assertThat(compose).doesNotContain("redis:", "minio:", "rabbitmq:");
	}

	@Test
	void nginxSupportsRestWebSocketAndSanitizesForwardedAddresses() throws IOException {
		String nginx = read("nginx/nginx.conf");

		assertThat(nginx).contains(
				"location /api/",
				"location /ws",
				"proxy_http_version 1.1",
				"proxy_set_header Upgrade $http_upgrade",
				"proxy_set_header Connection $connection_upgrade",
				"proxy_set_header X-Forwarded-For $remote_addr",
				"proxy_set_header X-Forwarded-Proto $scheme",
				"client_max_body_size 60m",
				"proxy_read_timeout 3600s",
				"location ^~ /actuator/");
		assertThat(nginx).doesNotContain(
				"$proxy_add_x_forwarded_for",
				"$request_uri",
				"$args",
				"$http_referer");
	}

	@Test
	void exampleEnvironmentAndBuildContextDoNotContainSecretValues() throws IOException {
		String environment = read(".env.example");
		String dockerignore = read(".dockerignore");

		assertThat(environment).contains(
				"DB_PASSWORD=\n",
				"JWT_SECRET=\n",
				"MAIL_PASSWORD=\n",
				"CORS_ALLOWED_ORIGINS=http://localhost:8080",
				"WEBSOCKET_ALLOWED_ORIGINS=http://localhost:8080");
		assertThat(dockerignore).contains(".git", ".env", "**/target", "**/node_modules");
	}

	@Test
	void deploymentGuideDocumentsVerificationAndSingleInstanceLimits() throws IOException {
		String documentation = read("docs/deployment.md");

		assertThat(documentation).contains(
				"docker compose config",
				"docker compose up -d",
				"/actuator/health/readiness",
				"docker compose down",
				"The Spring simple WebSocket broker is single-instance",
				"Local upload storage is single-instance",
				"The in-memory rate limiter is node-local",
				"CI/CD is not implemented");
	}

	private String read(String relativePath) throws IOException {
		return Files.readString(REPOSITORY_ROOT.resolve(relativePath));
	}
}
