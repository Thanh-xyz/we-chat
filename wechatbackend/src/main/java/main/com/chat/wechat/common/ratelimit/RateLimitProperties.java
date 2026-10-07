package main.com.chat.wechat.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
		Limit authLogin,
		Limit authRefresh,
		Limit authRegister,
		Limit authResendVerification,
		Limit messageSend,
		Limit websocketConnect,
		boolean distributedEnabled,
		String namespace,
		String environment,
		int fallbackCapacity,
		long fallbackRefillMinutes,
		int fallbackMaxEntries) {
	private static final Pattern SAFE_NAMESPACE = Pattern.compile("[A-Za-z0-9:_-]{1,96}");
	private static final Pattern SAFE_ENVIRONMENT = Pattern.compile("[A-Za-z0-9_-]{1,32}");

	@ConstructorBinding
	public RateLimitProperties {
		authLogin = authLogin == null ? new Limit(5, 1) : authLogin;
		authRefresh = authRefresh == null ? new Limit(20, 1) : authRefresh;
		authRegister = authRegister == null ? new Limit(5, 1) : authRegister;
		authResendVerification = authResendVerification == null ? new Limit(3, 15) : authResendVerification;
		messageSend = messageSend == null ? new Limit(60, 1) : messageSend;
		websocketConnect = websocketConnect == null ? new Limit(20, 1) : websocketConnect;
		namespace = namespace == null || namespace.isBlank() ? "webchat:ratelimit" : namespace.trim();
		environment = environment == null || environment.isBlank() ? "local" : environment.trim();
		if (!SAFE_NAMESPACE.matcher(namespace).matches()) {
			throw new IllegalArgumentException("app.rate-limit.namespace contains unsafe characters");
		}
		if (!SAFE_ENVIRONMENT.matcher(environment).matches()) {
			throw new IllegalArgumentException("app.rate-limit.environment contains unsafe characters");
		}
		if (fallbackCapacity <= 0) {
			fallbackCapacity = 1;
		}
		if (fallbackRefillMinutes <= 0) {
			fallbackRefillMinutes = 1;
		}
		if (fallbackMaxEntries < 100 || fallbackMaxEntries > 1_000_000) {
			fallbackMaxEntries = 10_000;
		}
	}

	public RateLimitProperties(
			Limit authLogin,
			Limit authRefresh,
			Limit authRegister,
			Limit authResendVerification,
			Limit messageSend,
			Limit websocketConnect) {
		this(authLogin, authRefresh, authRegister, authResendVerification, messageSend, websocketConnect,
				false, "webchat:ratelimit", "local", 1, 1, 10_000);
	}

	public Limit fallbackLimit() {
		return new Limit(fallbackCapacity, fallbackRefillMinutes);
	}

	public record Limit(int capacity, long refillMinutes) {
		public Limit {
			if (capacity <= 0) {
				capacity = 1;
			}
			if (refillMinutes <= 0) {
				refillMinutes = 1;
			}
		}
	}
}
