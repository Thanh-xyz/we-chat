package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

public class SmtpAuthEmailService implements AuthEmailService {
	private static final Logger LOGGER = LoggerFactory.getLogger(SmtpAuthEmailService.class);

	private final JavaMailSender mailSender;
	private final AuthEmailProperties emailProperties;
	private final AuthMailProperties mailProperties;

	public SmtpAuthEmailService(
			JavaMailSender mailSender,
			AuthEmailProperties emailProperties,
			AuthMailProperties mailProperties) {
		this.mailSender = mailSender;
		this.emailProperties = emailProperties;
		this.mailProperties = mailProperties;
	}

	@Override
	public void sendVerificationEmail(User user, String token, Instant expiresAt) {
		String body = "Verify your WeChat account before " + expiresAt + ":\n\n"
				+ emailProperties.emailVerificationUrl() + token;
		send(user, "email_verification", "Verify your WeChat account", body, expiresAt);
	}

	@Override
	public void sendPasswordResetEmail(User user, String token, Instant expiresAt) {
		String body = "Reset your WeChat password before " + expiresAt + ":\n\n"
				+ emailProperties.passwordResetUrl() + token
				+ "\n\nIf you did not request this, you can ignore this email.";
		send(user, "password_reset", "Reset your WeChat password", body, expiresAt);
	}

	private void send(User user, String event, String subject, String body, Instant expiresAt) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(mailProperties.from());
		message.setTo(user.email());
		message.setSubject(subject);
		message.setText(body);
		try {
			mailSender.send(message);
		} catch (MailException exception) {
			LOGGER.warn(
					"Authentication email delivery failed event={} userId={} failureType={}",
					event,
					user.id(),
					exception.getClass().getName());
			throw new IllegalStateException("Authentication email delivery failed");
		}
		LOGGER.info(
				"Authentication email delivered event={} userId={} email={} expiresAt={}",
				event,
				user.id(),
				user.email(),
				expiresAt);
	}
}
