package main.com.chat.wechat.common.security;

import main.com.chat.wechat.user.model.User;
import org.springframework.stereotype.Service;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class JwtTokenService {
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String JWT_ALGORITHM = "HS256";
	private static final String JWT_TYPE = "JWT";
	private static final int HMAC_SHA256_LENGTH_BYTES = 32;
	private static final int MAX_COMPACT_TOKEN_LENGTH = 8_192;
	private static final Pattern BASE64_URL_SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");
	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	private final JwtProperties jwtProperties;
	private final ObjectMapper objectMapper;
	private final ObjectReader jsonObjectReader;

	public JwtTokenService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
		this.jwtProperties = jwtProperties;
		this.objectMapper = objectMapper;
		this.jsonObjectReader = objectMapper.readerFor(JSON_OBJECT)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
	}

	public JwtToken createAccessToken(User user, List<String> roles, List<String> permissions) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperties.accessTokenTtl());

		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", JWT_ALGORITHM);
		header.put("typ", JWT_TYPE);

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
			if (token == null || token.length() > MAX_COMPACT_TOKEN_LENGTH) {
				return Optional.empty();
			}
			String[] parts = token.split("\\.", -1);
			if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
				return Optional.empty();
			}

			byte[] encodedHeader = decodeCanonicalBase64Url(parts[0]);
			byte[] encodedClaims = decodeCanonicalBase64Url(parts[1]);
			byte[] actualSignature = decodeCanonicalBase64Url(parts[2]);

			String signingInput = parts[0] + "." + parts[1];
			byte[] expectedSignature = hmac(signingInput);
			if (actualSignature.length != HMAC_SHA256_LENGTH_BYTES
					|| !MessageDigest.isEqual(expectedSignature, actualSignature)) {
				return Optional.empty();
			}

			Map<String, Object> header = readJsonObject(encodedHeader);
			if (!JWT_ALGORITHM.equals(requiredString(header, "alg"))) {
				return Optional.empty();
			}
			if (!JWT_TYPE.equals(requiredString(header, "typ"))) {
				return Optional.empty();
			}

			Map<String, Object> claims = readJsonObject(encodedClaims);
			if (!jwtProperties.issuer().equals(requiredString(claims, "iss"))) {
				return Optional.empty();
			}
			if (!"access".equals(requiredString(claims, "type"))) {
				return Optional.empty();
			}

			Instant now = Instant.now();
			Instant issuedAt = Instant.ofEpochSecond(requiredLong(claims, "iat"));
			Instant expiresAt = Instant.ofEpochSecond(requiredLong(claims, "exp"));
			if (issuedAt.isBefore(Instant.EPOCH)
					|| issuedAt.isAfter(now)
					|| !expiresAt.isAfter(now)
					|| !expiresAt.isAfter(issuedAt)) {
				return Optional.empty();
			}

			UUID userId = requiredUuid(claims, "sub");
			String username = requiredString(claims, "username");
			String email = requiredString(claims, "email");
			List<String> roles = roles(claims);
			List<String> permissions = optionalStringList(claims, "permissions");
			int tokenVersion = requiredNonNegativeInt(claims, "tokenVersion");
			return Optional.of(new JwtClaims(
					userId,
					username,
					email,
					roles,
					permissions,
					tokenVersion));
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
		return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
	}

	private byte[] decodeCanonicalBase64Url(String value) {
		if (!BASE64_URL_SEGMENT.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid Base64URL segment");
		}
		byte[] decoded = BASE64_URL_DECODER.decode(value);
		if (!BASE64_URL_ENCODER.encodeToString(decoded).equals(value)) {
			throw new IllegalArgumentException("Non-canonical Base64URL segment");
		}
		return decoded;
	}

	private Map<String, Object> readJsonObject(byte[] json) throws Exception {
		Map<String, Object> value = jsonObjectReader.readValue(json);
		if (value == null) {
			throw new IllegalArgumentException("JWT JSON segment must be an object");
		}
		return value;
	}

	private String requiredString(Map<String, Object> values, String name) {
		Object value = values.get(name);
		if (!(value instanceof String stringValue) || stringValue.isBlank()) {
			throw new IllegalArgumentException("Missing or invalid JWT string claim");
		}
		return stringValue;
	}

	private long requiredLong(Map<String, Object> values, String name) {
		Object value = values.get(name);
		if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
			return ((Number) value).longValue();
		}
		if (value instanceof BigInteger bigInteger) {
			return bigInteger.longValueExact();
		}
		throw new IllegalArgumentException("Missing or invalid JWT integer claim");
	}

	private int requiredNonNegativeInt(Map<String, Object> values, String name) {
		long value = requiredLong(values, name);
		if (value < 0 || value > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("JWT integer claim is out of range");
		}
		return (int) value;
	}

	private UUID requiredUuid(Map<String, Object> values, String name) {
		String value = requiredString(values, name);
		UUID uuid = UUID.fromString(value);
		if (!uuid.toString().equalsIgnoreCase(value)) {
			throw new IllegalArgumentException("JWT UUID claim is not canonical");
		}
		return uuid;
	}

	private List<String> roles(Map<String, Object> claims) {
		if (claims.containsKey("roles")) {
			return requiredStringList(claims.get("roles"));
		}
		if (claims.containsKey("role")) {
			return List.of(requiredString(claims, "role"));
		}
		return List.of();
	}

	private List<String> optionalStringList(Map<String, Object> values, String name) {
		if (!values.containsKey(name)) {
			return List.of();
		}
		return requiredStringList(values.get(name));
	}

	private List<String> requiredStringList(Object value) {
		if (!(value instanceof List<?> list)) {
			throw new IllegalArgumentException("Invalid JWT string-list claim");
		}
		List<String> result = new ArrayList<>(list.size());
		for (Object item : list) {
			if (!(item instanceof String stringItem) || stringItem.isBlank()) {
				throw new IllegalArgumentException("Invalid JWT string-list claim");
			}
			result.add(stringItem);
		}
		return List.copyOf(result);
	}
}
