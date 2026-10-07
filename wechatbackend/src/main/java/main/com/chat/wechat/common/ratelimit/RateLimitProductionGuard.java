package main.com.chat.wechat.common.ratelimit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class RateLimitProductionGuard {
	public RateLimitProductionGuard(RateLimitProperties properties) {
		if (!properties.distributedEnabled()) {
			throw new IllegalStateException("Distributed rate limiting must be enabled in the prod profile");
		}
	}
}
