package main.com.chat.wechat.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import main.com.chat.wechat.auth.dto.AuthResponse;
import main.com.chat.wechat.auth.dto.ChangePasswordRequest;
import main.com.chat.wechat.auth.dto.ForgotPasswordRequest;
import main.com.chat.wechat.auth.dto.LoginRequest;
import main.com.chat.wechat.auth.dto.RefreshTokenRequest;
import main.com.chat.wechat.auth.dto.RegisterRequest;
import main.com.chat.wechat.auth.dto.RegisterResponse;
import main.com.chat.wechat.auth.dto.ResendVerificationRequest;
import main.com.chat.wechat.auth.dto.ResetPasswordRequest;
import main.com.chat.wechat.auth.dto.VerifyEmailRequest;
import main.com.chat.wechat.auth.service.AuthService;
import main.com.chat.wechat.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return authService.login(request, httpRequest);
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
		return authService.refresh(request.refreshToken(), httpRequest);
	}

	@PostMapping("/refresh-token")
	public AuthResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
		return authService.refresh(request.refreshToken(), httpRequest);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
		authService.logout(request.refreshToken(), httpRequest);
	}

	@PostMapping("/logout-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logoutAll(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest httpRequest) {
		authService.logoutAll(principal, httpRequest);
	}

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.forgotPassword(request);
	}

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
		authService.resetPassword(request, httpRequest);
	}

	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {
		authService.verifyEmail(request, httpRequest);
	}

	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		authService.resendVerification(request);
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody ChangePasswordRequest request,
			HttpServletRequest httpRequest) {
		authService.changePassword(principal, request, httpRequest);
	}
}
