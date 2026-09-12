package main.com.chat.wechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
		List<String> allowedOrigins,
		List<String> allowedMethods,
		List<String> allowedHeaders,
		List<String> exposedHeaders) {
	public CorsProperties {
		allowedOrigins = normalized(allowedOrigins);
		allowedMethods = normalized(allowedMethods);
		allowedHeaders = normalized(allowedHeaders);
		exposedHeaders = normalized(exposedHeaders);
		rejectWildcardOrNullOrigins(allowedOrigins);
		rejectWildcards("methods", allowedMethods);
		rejectWildcards("headers", allowedHeaders);
		rejectWildcards("exposed headers", exposedHeaders);
	}

	private static List<String> normalized(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.map(value -> value == null ? "" : value.trim())
				.filter(value -> !value.isEmpty())
				.toList();
	}

	private static void rejectWildcardOrNullOrigins(List<String> origins) {
		if (origins.stream().anyMatch(origin -> origin.contains("*") || origin.equalsIgnoreCase("null"))) {
			throw new IllegalArgumentException("app.cors.allowed-origins must contain exact non-null origins");
		}
	}

	private static void rejectWildcards(String name, List<String> values) {
		if (values.stream().anyMatch(value -> value.contains("*"))) {
			throw new IllegalArgumentException("app.cors.allowed-" + name + " must not contain wildcards");
		}
	}
}
