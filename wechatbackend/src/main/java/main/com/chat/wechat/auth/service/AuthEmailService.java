package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.user.model.User;

import java.time.Instant;

public interface AuthEmailService {
	void sendVerificationEmail(User user, String token, Instant expiresAt);

	void sendPasswordResetEmail(User user, String token, Instant expiresAt);
}
