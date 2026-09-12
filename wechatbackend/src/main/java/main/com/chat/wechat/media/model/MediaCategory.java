package main.com.chat.wechat.media.model;

import main.com.chat.wechat.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.Locale;

public enum MediaCategory {
	IMAGE,
	VIDEO,
	VOICE,
	FILE;

	public static MediaCategory from(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return MediaCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported media category: " + value);
		}
	}
}
