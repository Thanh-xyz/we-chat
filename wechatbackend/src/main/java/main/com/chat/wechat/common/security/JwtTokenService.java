package main.com.chat.wechat.common.security;

import main.com.chat.wechat.user.model.User;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService {
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	private final JwtProperties jwtProperties;
	private final ObjectMapper objectMapper;

	public JwtTokenService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
		this.jwtProperties = jwtProperties;
		this.objectMapper = objectMapper;
	}

	public JwtToken createAccessToken(User user, List<String> roles, List<String> permissions) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperties.accessTokenTtl());

		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");

		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", jwtProperties.issuer());
		claims.put("sub", user.id().toString());
		claims.put("username", user.username());
		claims.put("email", user.email());
		claims.put("roles", roles);
		claims.put("permissions", permissions);
		claims.put("tokenVersion", user.tokenVersion());
		claims.put("type", "access");
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", expiresAt.getEpochSecond());
		claims.put("jti", UUID.randomUUID().toString());

		return new JwtToken(sign(header, claims), expiresAt);
	}

	public Optional<JwtClaims> validateAccessToken(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				return Optional.empty();
			}

			String signingInput = parts[0] + "." + parts[1];
			byte[] expectedSignature = hmac(signingInput);
			byte[] actualSignature = BASE64_URL_DECODER.decode(parts[2]);
			if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
				return Optional.empty();
			}

			Map<String, Object> claims = objectMapper.readValue(
					BASE64_URL_DECODER.decode(parts[1]),
					new TypeReference<>() {
					});

			if (!jwtProperties.issuer().equals(asString(claims.get("iss")))) {
				return Optional.empty();
			}
			if (!"access".equals(asString(claims.get("type")))) {
				return Optional.empty();
			}
			if (Instant.ofEpochSecond(asLong(claims.get("exp"))).isBefore(Instant.now())) {
				return Optional.empty();
			}

			List<String> roles = asStringList(claims.get("roles"));
			if (roles.isEmpty()) {
				roles = asStringList(claims.get("role"));
			}
			return Optional.of(new JwtClaims(
					UUID.fromString(asString(claims.get("sub"))),
					asString(claims.get("username")),
					asString(claims.get("email")),
					roles,
					asStringList(claims.get("permissions")),
					(int) asLong(claims.getOrDefault("tokenVersion", 0))));
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	private String sign(Map<String, Object> header, Map<String, Object> claims) {
		try {
			String encodedHeader = BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(header));
			String encodedClaims = BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
			String signingInput = encodedHeader + "." + encodedClaims;
			String encodedSignature = BASE64_URL_ENCODER.encodeToString(hmac(signingInput));
			return signingInput + "." + encodedSignature;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not create JWT", exception);
		}
	}

	private byte[] hmac(String signingInput) throws Exception {
		Mac mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
		return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
	}

	private String asString(Object value) {
		return value == null ? null : value.toString();
	}

	private long asLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(value.toString());
	}

	private List<String> asStringList(Object value) {
		if (value instanceof List<?> list) {
			return list.stream().map(Object::toString).toList();
		}
		String legacyRole = asString(value);
		return legacyRole == null || legacyRole.isBlank() ? List.of() : List.of(legacyRole);
	}
}
