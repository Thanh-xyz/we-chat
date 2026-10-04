package main.com.chat.wechat.common.search;

import main.com.chat.wechat.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class SearchQueryPolicy {
	public static final int MAX_LENGTH = 100;

	private SearchQueryPolicy() {
	}

	public static void validate(String query) {
		if (query != null && query.length() > MAX_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Search query must be 100 characters or fewer");
		}
	}
}
