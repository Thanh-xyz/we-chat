package main.com.chat.wechat.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtTokenService jwtTokenService;
	private final UserAuthStateService userAuthStateService;

	public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserAuthStateService userAuthStateService) {
		this.jwtTokenService = jwtTokenService;
		this.userAuthStateService = userAuthStateService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String token = resolveBearerToken(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			jwtTokenService.validateAccessToken(token).ifPresent(claims -> {
				boolean currentUserValid = userAuthStateService.find(claims.userId())
						.filter(state -> state.active() && state.tokenVersion() == claims.tokenVersion())
						.isPresent();
				if (!currentUserValid) {
					return;
				}
				AuthenticatedUser principal = new AuthenticatedUser(
						claims.userId(),
						claims.username(),
						claims.email(),
						claims.roles(),
						claims.permissions(),
						claims.tokenVersion());
				List<SimpleGrantedAuthority> authorities = new ArrayList<>();
				claims.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
				claims.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						principal,
						null,
						authorities);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			});
		}

		filterChain.doFilter(request, response);
	}

	private String resolveBearerToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
			return null;
		}
		return authorization.substring(7);
	}
}
