package main.com.chat.wechat.common.security;

import main.com.chat.wechat.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {
	private static final String SECRET = "test-secret-test-secret-test-secret-1234";
	private static final String ISSUER = "wechat-test";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
	private static final String BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final JwtTokenService jwtTokenService = new JwtTokenService(
			new JwtProperties(SECRET, ISSUER, Duration.ofMinutes(15), Duration.ofDays(30)),
			objectMapper);

	@Test
	void createsAndValidatesRequiredAccessTokenClaims() throws Exception {
		User user = user();

		JwtToken token = jwtTokenService.createAccessToken(user, List.of("USER"), List.of("MESSAGE_SEND"));
		JwtClaims claims = jwtTokenService.validateAccessToken(token.value()).orElseThrow();
		Map<String, Object> header = decodeJson(token.value().split("\\.", -1)[0]);

		assertThat(header).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
		assertThat(claims.userId()).isEqualTo(user.id());
		assertThat(claims.username()).isEqualTo(user.username());
		assertThat(claims.roles()).containsExactly("USER");
		assertThat(claims.permissions()).containsExactly("MESSAGE_SEND");
		assertThat(claims.tokenVersion()).isEqualTo(7);
	}

	@Test
	void validatesTheExactCompactSigningInputWithoutJsonNormalization() throws Exception {
		String encodedHeader = encodeRawJson("{ \"typ\" : \"JWT\", \"alg\" : \"HS256\" }");
		Map<String, Object> claims = validClaims();
		claims.put("username", "Người dùng");
		String encodedClaims = encodeJson(claims);

		String token = signEncoded(encodedHeader, encodedClaims);

		assertThat(jwtTokenService.validateAccessToken(token)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {"none", "HS384", "HS512", "RS256"})
	void rejectsEveryAlgorithmExceptHs256EvenWithAValidHs256Signature(String algorithm) throws Exception {
		Map<String, Object> header = validHeader();
		header.put("alg", algorithm);

		assertThat(jwtTokenService.validateAccessToken(signedToken(header, validClaims()))).isEmpty();
	}

	@Test
	void rejectsMissingOrMalformedAlgorithmAndDuplicateHeaderMembers() throws Exception {
		Map<String, Object> missing = validHeader();
		missing.remove("alg");
		Map<String, Object> malformed = validHeader();
		malformed.put("alg", List.of("HS256"));
		String duplicate = signEncoded(
				encodeRawJson("{\"alg\":\"none\",\"alg\":\"HS256\",\"typ\":\"JWT\"}"),
				encodeJson(validClaims()));

		assertThat(jwtTokenService.validateAccessToken(signedToken(missing, validClaims()))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(malformed, validClaims()))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(duplicate)).isEmpty();
	}

	@Test
	void requiresTheJwtTokenType() throws Exception {
		Map<String, Object> missing = validHeader();
		missing.remove("typ");
		Map<String, Object> wrong = validHeader();
		wrong.put("typ", "JWE");
		Map<String, Object> malformed = validHeader();
		malformed.put("typ", 123);

		assertThat(jwtTokenService.validateAccessToken(signedToken(missing, validClaims()))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(wrong, validClaims()))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(malformed, validClaims()))).isEmpty();
	}

	@Test
	void rejectsModifiedHeaderPayloadAndSignature() throws Exception {
		String valid = signedToken(validHeader(), validClaims());
		String[] parts = valid.split("\\.", -1);
		Map<String, Object> changedHeader = validHeader();
		changedHeader.put("extra", true);
		Map<String, Object> changedClaims = validClaims();
		changedClaims.put("username", "attacker");
		String changedHeaderToken = encodeJson(changedHeader) + "." + parts[1] + "." + parts[2];
		String changedPayloadToken = parts[0] + "." + encodeJson(changedClaims) + "." + parts[2];
		String changedSignatureToken = parts[0] + "." + parts[1] + "." + replaceFirstCharacter(parts[2]);

		assertThat(jwtTokenService.validateAccessToken(changedHeaderToken)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(changedPayloadToken)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(changedSignatureToken)).isEmpty();
	}

	@Test
	void rejectsTruncatedRandomWrongLengthAndNonCanonicalSignatures() throws Exception {
		String valid = signedToken(validHeader(), validClaims());
		String[] parts = valid.split("\\.", -1);
		String truncated = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 4);
		String random = parts[0] + "." + parts[1] + "." + BASE64_URL_ENCODER.encodeToString(new byte[32]);
		String wrongLength = parts[0] + "." + parts[1] + "." + BASE64_URL_ENCODER.encodeToString(new byte[31]);
		String nonCanonicalSignature = nonCanonicalEquivalent(parts[2]);
		String nonCanonical = parts[0] + "." + parts[1] + "." + nonCanonicalSignature;

		assertThat(jwtTokenService.validateAccessToken(truncated)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(random)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(wrongLength)).isEmpty();
		assertThat(BASE64_URL_DECODER.decode(nonCanonicalSignature))
				.containsExactly(BASE64_URL_DECODER.decode(parts[2]));
		assertThat(jwtTokenService.validateAccessToken(nonCanonical)).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			" ",
			"a",
			"a.b",
			"a.b.c.d",
			".b.c",
			"a..c",
			"a.b.",
			"..."
	})
	void rejectsInvalidCompactTokenStructure(String token) {
		assertThat(jwtTokenService.validateAccessToken(token)).isEmpty();
	}

	@Test
	void rejectsInvalidBase64UrlMalformedJsonAndTrailingJson() throws Exception {
		String payload = encodeJson(validClaims());
		String invalidHeaderBase64 = signEncoded("invalid*base64", payload);
		String invalidPayloadBase64 = signEncoded(encodeJson(validHeader()), "invalid*base64");
		String malformedHeaderJson = signEncoded(encodeRawJson("{not-json"), payload);
		String malformedPayloadJson = signEncoded(encodeJson(validHeader()), encodeRawJson("[1, 2, 3]"));
		String trailingHeaderJson = signEncoded(
				encodeRawJson("{\"alg\":\"HS256\",\"typ\":\"JWT\"} {}"),
				payload);

		assertThat(jwtTokenService.validateAccessToken(invalidHeaderBase64)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(invalidPayloadBase64)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(malformedHeaderJson)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(malformedPayloadJson)).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(trailingHeaderJson)).isEmpty();
	}

	@Test
	void rejectsExcessivelyLargeTokensBeforeDecodingOrJsonParsing() {
		assertThat(jwtTokenService.validateAccessToken("a".repeat(8_193))).isEmpty();
	}

	@Test
	void acceptsFutureExpirationAndRejectsExpiredExactBoundaryMissingOrNullExpiration() throws Exception {
		long now = Instant.now().getEpochSecond();
		Map<String, Object> future = validClaims();
		future.put("exp", now + 600);
		Map<String, Object> expired = validClaims();
		expired.put("exp", now - 1);
		Map<String, Object> boundary = validClaims();
		boundary.put("exp", now);
		Map<String, Object> missing = validClaims();
		missing.remove("exp");
		Map<String, Object> nullExpiration = validClaims();
		nullExpiration.put("exp", null);

		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), future))).isPresent();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), expired))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), boundary))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), missing))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), nullExpiration))).isEmpty();
	}

	@Test
	void rejectsDecimalStringNegativeOverflowingAndExtremelyLargeExpiration() throws Exception {
		long future = Instant.now().plusSeconds(600).getEpochSecond();
		assertClaimRejected("exp", new BigDecimal(future + ".5"));
		assertClaimRejected("exp", Long.toString(future));
		assertClaimRejected("exp", -1);
		assertClaimRejected("exp", Long.MAX_VALUE);
		assertClaimRejected("exp", new BigInteger("999999999999999999999999999999999999"));
	}

	@Test
	void requiresAValidIssuedAtTimestampThatPrecedesExpiration() throws Exception {
		long now = Instant.now().getEpochSecond();
		Map<String, Object> missing = validClaims();
		missing.remove("iat");
		Map<String, Object> future = validClaims();
		future.put("iat", now + 60);
		Map<String, Object> afterExpiration = validClaims();
		afterExpiration.put("iat", ((Number) afterExpiration.get("exp")).longValue());

		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), missing))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), future))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), afterExpiration))).isEmpty();
		assertClaimRejected("iat", "1234567890");
		assertClaimRejected("iat", new BigDecimal("1234567890.5"));
		assertClaimRejected("iat", -1);
	}

	@Test
	void rejectsMissingMalformedOrNonCanonicalSubject() throws Exception {
		Map<String, Object> missing = validClaims();
		missing.remove("sub");
		Map<String, Object> malformed = validClaims();
		malformed.put("sub", "not-a-uuid");
		Map<String, Object> abbreviated = validClaims();
		abbreviated.put("sub", "1-1-1-1-1");
		Map<String, Object> wrongType = validClaims();
		wrongType.put("sub", 123);

		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), missing))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), malformed))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), abbreviated))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), wrongType))).isEmpty();
	}

	@Test
	void rejectsMissingMalformedNegativeOrOverflowingTokenVersion() throws Exception {
		Map<String, Object> missing = validClaims();
		missing.remove("tokenVersion");
		Map<String, Object> nullVersion = validClaims();
		nullVersion.put("tokenVersion", null);

		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), missing))).isEmpty();
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), nullVersion))).isEmpty();
		assertClaimRejected("tokenVersion", "7");
		assertClaimRejected("tokenVersion", new BigDecimal("7.5"));
		assertClaimRejected("tokenVersion", -1);
		assertClaimRejected("tokenVersion", (long) Integer.MAX_VALUE + 1);
	}

	@Test
	void rejectsWrongIssuerTokenTypeAndUnsafeIdentityOrAuthorityClaimTypes() throws Exception {
		assertClaimRejected("iss", "another-issuer");
		assertClaimRejected("type", "refresh");
		assertClaimRejected("username", 123);
		assertClaimRejected("email", List.of("user@example.com"));
		assertClaimRejected("roles", "ADMIN");
		assertClaimRejected("roles", List.of("USER", 123));
		assertClaimRejected("permissions", "MESSAGE_SEND");
		assertClaimRejected("permissions", Arrays.asList("MESSAGE_SEND", null));
	}

	private void assertClaimRejected(String name, Object value) throws Exception {
		Map<String, Object> claims = validClaims();
		claims.put(name, value);
		assertThat(jwtTokenService.validateAccessToken(signedToken(validHeader(), claims)))
				.as("claim %s with value %s", name, value)
				.isEmpty();
	}

	private Map<String, Object> validHeader() {
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");
		return header;
	}

	private Map<String, Object> validClaims() {
		long now = Instant.now().getEpochSecond();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", ISSUER);
		claims.put("sub", "00000000-0000-0000-0000-000000000001");
		claims.put("username", "user");
		claims.put("email", "user@example.com");
		claims.put("roles", List.of("USER"));
		claims.put("permissions", List.of("MESSAGE_SEND"));
		claims.put("tokenVersion", 7);
		claims.put("type", "access");
		claims.put("iat", now - 1);
		claims.put("exp", now + 600);
		claims.put("jti", "00000000-0000-0000-0000-000000000099");
		return claims;
	}

	private String signedToken(Map<String, Object> header, Map<String, Object> claims) throws Exception {
		return signEncoded(encodeJson(header), encodeJson(claims));
	}

	private String signEncoded(String encodedHeader, String encodedClaims) throws Exception {
		String signingInput = encodedHeader + "." + encodedClaims;
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String signature = BASE64_URL_ENCODER.encodeToString(
				mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
		return signingInput + "." + signature;
	}

	private String encodeJson(Object value) throws Exception {
		return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
	}

	private String encodeRawJson(String json) {
		return BASE64_URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	private Map<String, Object> decodeJson(String encoded) throws Exception {
		return objectMapper.readValue(BASE64_URL_DECODER.decode(encoded), new TypeReference<>() {
		});
	}

	private String replaceFirstCharacter(String value) {
		char replacement = value.charAt(0) == 'A' ? 'B' : 'A';
		return replacement + value.substring(1);
	}

	private String nonCanonicalEquivalent(String value) {
		int lastIndex = value.length() - 1;
		int alphabetIndex = BASE64_URL_ALPHABET.indexOf(value.charAt(lastIndex));
		return value.substring(0, lastIndex) + BASE64_URL_ALPHABET.charAt(alphabetIndex + 1);
	}

	private User user() {
		Instant now = Instant.now();
		return new User(
				UUID.fromString("00000000-0000-0000-0000-000000000001"),
				"user",
				"user@example.com",
				"hash",
				"User",
				null,
				"OFFLINE",
				"USER",
				true,
				"ACTIVE",
				true,
				7,
				null,
				0,
				null,
				null,
				now,
				now);
	}
}
