package main.com.chat.wechat.realtime.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class RealtimeProductionGuard {
	public RealtimeProductionGuard(RealtimeProperties properties) {
		if (!properties.distributedEnabled()) {
			throw new IllegalStateException("Distributed realtime bridge must be enabled in the prod profile");
		}
	}
}
