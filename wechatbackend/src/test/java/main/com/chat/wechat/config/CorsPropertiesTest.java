package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {
	@Test
	void blankOriginEntriesAreRemovedAndPoliciesRemainExact() {
		CorsProperties properties = new CorsProperties(
				List.of(" http://localhost:5173 ", "", "  "),
				List.of("GET", "POST"),
				List.of("Authorization", "Content-Type"),
				List.of("X-Request-ID"));

		assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5173");
		assertThat(properties.allowedMethods()).containsExactly("GET", "POST");
		assertThat(properties.allowedHeaders()).containsExactly("Authorization", "Content-Type");
		assertThat(properties.exposedHeaders()).containsExactly("X-Request-ID");
	}

	@ParameterizedTest
	@ValueSource(strings = {"*", "https://*.example.com", "null"})
	void rejectsWildcardOrNullOrigins(String origin) {
		assertThatThrownBy(() -> new CorsProperties(
				List.of(origin),
				List.of("GET"),
				List.of("Authorization"),
				List.of("X-Request-ID")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsWildcardMethodsHeadersAndExposedHeaders() {
		assertThatThrownBy(() -> new CorsProperties(
				List.of("http://localhost:5173"),
				List.of("*"),
				List.of("Authorization"),
				List.of("X-Request-ID")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CorsProperties(
				List.of("http://localhost:5173"),
				List.of("GET"),
				List.of("*"),
				List.of("X-Request-ID")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CorsProperties(
				List.of("http://localhost:5173"),
				List.of("GET"),
				List.of("Authorization"),
				List.of("*")))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
