package main.com.chat.wechat.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void missingMultipartFilePartReturnsBadRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/attachments/upload");

		var response = handler.handleMissingRequestPart(new MissingServletRequestPartException("file"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(400);
		assertThat(response.getBody().message()).isEqualTo("Required request part is missing: file");
		assertThat(response.getBody().path()).isEqualTo("/api/attachments/upload");
	}
}
