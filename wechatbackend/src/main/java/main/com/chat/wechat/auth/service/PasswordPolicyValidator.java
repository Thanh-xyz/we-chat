package main.com.chat.wechat.auth.service;

import main.com.chat.wechat.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {
	public void validate(String password) {
		if (password == null || password.length() < 8 || password.length() > 128) {
			throw invalid();
		}
		boolean hasUpper = false;
		boolean hasLower = false;
		boolean hasDigit = false;
		boolean hasSpecial = false;
		for (int index = 0; index < password.length(); index++) {
			char current = password.charAt(index);
			if (Character.isUpperCase(current)) {
				hasUpper = true;
			} else if (Character.isLowerCase(current)) {
				hasLower = true;
			} else if (Character.isDigit(current)) {
				hasDigit = true;
			} else {
				hasSpecial = true;
			}
		}
		if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
			throw invalid();
		}
	}

	private ApiException invalid() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"Password must be 8-128 characters and include uppercase, lowercase, number, and special character");
	}
}
