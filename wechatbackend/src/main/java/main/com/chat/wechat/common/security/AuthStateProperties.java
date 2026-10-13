package main.com.chat.wechat.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "app.auth-state")
public record AuthStateProperties(
		boolean enabled,
		String namespace,
		String environment,
		Duration ttl) {
	private static final Pattern SAFE_NAMESPACE = Pattern.compile("[A-Za-z0-9:_-]{1,96}");
	private static final Pattern SAFE_ENVIRONMENT = Pattern.compile("[A-Za-z0-9_-]{1,32}");
	private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);
	private static final Duration MIN_TTL = Duration.ofSeconds(30);
	private static final Duration MAX_TTL = Duration.ofSeconds(60);

	public AuthStateProperties {
		namespace = namespace == null || namespace.isBlank() ? "webchat:auth-state" : namespace.trim();
		environment = environment == null || environment.isBlank() ? "local" : environment.trim();
		ttl = ttl == null ? DEFAULT_TTL : ttl;
		if (!SAFE_NAMESPACE.matcher(namespace).matches()) {
			throw new IllegalArgumentException("app.auth-state.namespace contains unsafe characters");
		}
		if (!SAFE_ENVIRONMENT.matcher(environment).matches()) {
			throw new IllegalArgumentException("app.auth-state.environment contains unsafe characters");
		}
		if (ttl.compareTo(MIN_TTL) < 0 || ttl.compareTo(MAX_TTL) > 0) {
			throw new IllegalArgumentException("app.auth-state.ttl must be between 30 and 60 seconds");
		}
	}
}
