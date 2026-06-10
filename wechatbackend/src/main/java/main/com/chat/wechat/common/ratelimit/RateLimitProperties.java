package main.com.chat.wechat.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
		Limit authLogin,
		Limit authRefresh,
		Limit authRegister,
		Limit messageSend,
		Limit websocketConnect) {

	public RateLimitProperties {
		authLogin = authLogin == null ? new Limit(5, 1) : authLogin;
		authRefresh = authRefresh == null ? new Limit(20, 1) : authRefresh;
		authRegister = authRegister == null ? new Limit(5, 1) : authRegister;
		messageSend = messageSend == null ? new Limit(60, 1) : messageSend;
		websocketConnect = websocketConnect == null ? new Limit(20, 1) : websocketConnect;
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
