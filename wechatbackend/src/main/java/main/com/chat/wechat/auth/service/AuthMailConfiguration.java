package main.com.chat.wechat.auth.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration(proxyBeanMethods = false)
@Profile("prod | (!local & !test)")
@EnableConfigurationProperties({AuthMailProperties.class, AuthEmailProperties.class})
public class AuthMailConfiguration {
	@Bean
	JavaMailSender authMailSender(AuthMailProperties properties) {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setProtocol("smtp");
		sender.setHost(properties.host());
		sender.setPort(properties.port());
		sender.setUsername(properties.username());
		sender.setPassword(properties.password());
		sender.setDefaultEncoding("UTF-8");

		Properties javaMailProperties = sender.getJavaMailProperties();
		javaMailProperties.put("mail.smtp.auth", "true");
		javaMailProperties.put("mail.smtp.starttls.enable", "true");
		javaMailProperties.put("mail.smtp.starttls.required", "true");
		javaMailProperties.put("mail.smtp.connectiontimeout", "10000");
		javaMailProperties.put("mail.smtp.timeout", "10000");
		javaMailProperties.put("mail.smtp.writetimeout", "10000");
		javaMailProperties.put("mail.debug", "false");
		return sender;
	}

	@Bean
	AuthEmailService smtpAuthEmailService(
			JavaMailSender mailSender,
			AuthEmailProperties emailProperties,
			AuthMailProperties mailProperties) {
		return new SmtpAuthEmailService(mailSender, emailProperties, mailProperties);
	}
}
