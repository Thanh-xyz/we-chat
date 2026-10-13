package main.com.chat.wechat.common.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private JwtTokenService jwtTokenService;

	@Mock
	private UserAuthStateService userAuthStateService;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void restRequestAcceptsCurrentSharedAuthState() throws ServletException, IOException {
		when(jwtTokenService.validateAccessToken("valid-token")).thenReturn(Optional.of(claims(3)));
		when(userAuthStateService.find(USER_ID)).thenReturn(Optional.of(new AuthState(USER_ID, 3, true)));
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService, userAuthStateService);
		MockHttpServletRequest request = request("valid-token");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		verify(userAuthStateService).find(USER_ID);
	}

	@Test
	void restRequestRejectsStaleTokenVersionFromSharedAuthState() throws ServletException, IOException {
		when(jwtTokenService.validateAccessToken("stale-token")).thenReturn(Optional.of(claims(3)));
		when(userAuthStateService.find(USER_ID)).thenReturn(Optional.of(new AuthState(USER_ID, 4, true)));
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService, userAuthStateService);

		filter.doFilter(request("stale-token"), new MockHttpServletResponse(), new MockFilterChain());

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private MockHttpServletRequest request(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}

	private JwtClaims claims(int tokenVersion) {
		return new JwtClaims(USER_ID, "user", "user@example.com", List.of("USER"), List.of(), tokenVersion);
	}
}
