package main.com.chat.wechat.config;

import main.com.chat.wechat.attachment.service.AttachmentProperties;
import main.com.chat.wechat.attachment.storage.StorageProperties;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ErrorResponse;
import main.com.chat.wechat.common.ratelimit.RateLimitFilter;
import main.com.chat.wechat.common.ratelimit.RateLimitProperties;
import main.com.chat.wechat.common.security.JwtAuthenticationFilter;
import main.com.chat.wechat.common.security.JwtProperties;
import main.com.chat.wechat.common.security.LoginSecurityProperties;
import main.com.chat.wechat.common.security.RbacProperties;
import main.com.chat.wechat.common.web.RequestIdFilter;
import main.com.chat.wechat.auth.service.AuthEmailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Configuration
@EnableConfigurationProperties({
		JwtProperties.class,
		RbacProperties.class,
		AuthEmailProperties.class,
		RateLimitProperties.class,
		LoginSecurityProperties.class,
		StorageProperties.class,
		AttachmentProperties.class
})
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			RequestIdFilter requestIdFilter,
			RateLimitFilter rateLimitFilter,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			AuditLogService auditLogService,
			ObjectMapper objectMapper) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/api/auth/register",
								"/api/auth/login",
								"/api/auth/refresh",
								"/api/auth/refresh-token",
								"/api/auth/forgot-password",
								"/api/auth/reset-password",
								"/api/auth/verify-email",
								"/api/auth/resend-verification").permitAll()
						.requestMatchers("/api/admin/**", "/admin/**").authenticated()
						.anyRequest().authenticated())
				.exceptionHandling(exceptionHandling -> exceptionHandling
						.accessDeniedHandler((request, response, exception) -> {
							auditLogService.logFailure(
									"SECURITY_ACCESS_DENIED",
									"REQUEST",
									request.getRequestURI(),
									"Access denied",
									null,
									request);
							response.setStatus(HttpStatus.FORBIDDEN.value());
							response.setContentType("application/json");
							objectMapper.writeValue(response.getWriter(), new ErrorResponse(
									Instant.now(),
									HttpStatus.FORBIDDEN.value(),
									HttpStatus.FORBIDDEN.getReasonPhrase(),
									"Access denied",
									request.getRequestURI(),
									null));
						})
						.authenticationEntryPoint((request, response, exception) -> {
							response.setStatus(HttpStatus.UNAUTHORIZED.value());
							response.setContentType("application/json");
							objectMapper.writeValue(response.getWriter(), new ErrorResponse(
									Instant.now(),
									HttpStatus.UNAUTHORIZED.value(),
									HttpStatus.UNAUTHORIZED.getReasonPhrase(),
									"Authentication required",
									request.getRequestURI(),
									null));
						}))
				.addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException(username);
		};
	}
}
