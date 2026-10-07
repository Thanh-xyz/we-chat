package main.com.chat.wechat.common.ratelimit;

public interface RateLimiter {
	boolean tryConsume(String operation, RateLimitKey key, RateLimitProperties.Limit limit);
}
