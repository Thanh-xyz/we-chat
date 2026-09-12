package main.com.chat.wechat.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
	private static final String INTERNAL_ERROR = "connection failed at /internal/path UserRepository";
	private static final String INTERNAL_SQL = "insert into users(password_hash) values ('sensitive-hash')";
	private static final String RAW_TOKEN = "Bearer sensitive-authentication-token";

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new FailureController())
			.setControllerAdvice(handler)
			.build();

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

	@Test
	void unexpectedExceptionDoesNotExposeTraceClassOrInternalMessage() throws Exception {
		mockMvc.perform(get("/test/errors/unhandled"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("Unexpected server error"))
				.andExpect(jsonPath("$.exception").doesNotExist())
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.cause").doesNotExist())
				.andExpect(content().string(not(containsString(INTERNAL_ERROR))))
				.andExpect(content().string(not(containsString(IllegalStateException.class.getName()))));
	}

	@Test
	void validationErrorOnlyExposesSafeFieldInformation() throws Exception {
		mockMvc.perform(post("/test/errors/validation")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.validationErrors.displayName").value("must not be blank"))
				.andExpect(jsonPath("$.exception").doesNotExist())
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	@Test
	void databaseExceptionDoesNotExposeSqlOrBindValues() throws Exception {
		mockMvc.perform(get("/test/errors/database"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Resource already exists"))
				.andExpect(jsonPath("$.sql").doesNotExist())
				.andExpect(content().string(not(containsString(INTERNAL_SQL))))
				.andExpect(content().string(not(containsString("sensitive-hash"))));
	}

	@Test
	void authenticationExceptionDoesNotExposeTokenDetails() throws Exception {
		mockMvc.perform(get("/test/errors/authentication"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication required"))
				.andExpect(content().string(not(containsString(RAW_TOKEN))));
	}

	@Test
	void authorizationExceptionDoesNotExposeImplementationDetails() throws Exception {
		mockMvc.perform(get("/test/errors/authorization"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Access denied"))
				.andExpect(content().string(not(containsString("internal ACL implementation"))));
	}

	@RestController
	static class FailureController {
		@GetMapping("/test/errors/unhandled")
		void unhandled() {
			throw new IllegalStateException(INTERNAL_ERROR);
		}

		@PostMapping("/test/errors/validation")
		void validation(@Valid @RequestBody ValidationRequest request) {
		}

		@GetMapping("/test/errors/database")
		void database() {
			throw new DuplicateKeyException(INTERNAL_SQL);
		}

		@GetMapping("/test/errors/authentication")
		void authentication() {
			throw new BadCredentialsException(RAW_TOKEN);
		}

		@GetMapping("/test/errors/authorization")
		void authorization() {
			throw new AccessDeniedException("internal ACL implementation: admin table");
		}
	}

	record ValidationRequest(@NotBlank(message = "must not be blank") String displayName) {
	}
}
