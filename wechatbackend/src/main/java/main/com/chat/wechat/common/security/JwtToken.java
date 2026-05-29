package main.com.chat.wechat.common.security;

import java.time.Instant;

public record JwtToken(String value, Instant expiresAt) {
}
