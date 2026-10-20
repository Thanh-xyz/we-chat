package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.user.model.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("prod | (!local & !test)")
@ConditionalOnProperty(prefix = "app.auth", name = "email-enabled", havingValue = "false")
public class DisabledAuthEmailService implements AuthEmailService {
	@Override
	public void sendVerificationEmail(User user, String token, Instant expiresAt) {
		throw new IllegalStateException("Authentication email sending is disabled");
	}

	@Override
	public void sendPasswordResetEmail(User user, String token, Instant expiresAt) {
		throw new IllegalStateException("Authentication email sending is disabled");
	}
}
