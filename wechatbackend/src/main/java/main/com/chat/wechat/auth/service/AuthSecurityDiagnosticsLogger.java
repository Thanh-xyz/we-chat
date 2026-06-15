package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.common.security.JwtProperties;
import main.com.chat.wechat.common.security.LoginSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class AuthSecurityDiagnosticsLogger implements ApplicationRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthSecurityDiagnosticsLogger.class);

	private final LoginSecurityProperties loginSecurityProperties;
	private final JwtProperties jwtProperties;
	private final JdbcTemplate jdbcTemplate;

	public AuthSecurityDiagnosticsLogger(
			LoginSecurityProperties loginSecurityProperties,
			JwtProperties jwtProperties,
			JdbcTemplate jdbcTemplate) {
		this.loginSecurityProperties = loginSecurityProperties;
		this.jwtProperties = jwtProperties;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		LOGGER.info(
				"Auth security runtime config emailVerificationTokenTtl={} passwordResetTokenTtl={} accessTokenTtl={} refreshTokenTtl={} jvmZone={} databaseTimezone={}",
				loginSecurityProperties.emailVerificationTokenTtl(),
				loginSecurityProperties.passwordResetTokenTtl(),
				jwtProperties.accessTokenTtl(),
				jwtProperties.refreshTokenTtl(),
				ZoneId.systemDefault(),
				databaseTimezone());
	}

	private String databaseTimezone() {
		try {
			return jdbcTemplate.queryForObject("show timezone", String.class);
		} catch (Exception exception) {
			return "unavailable";
		}
	}
}
