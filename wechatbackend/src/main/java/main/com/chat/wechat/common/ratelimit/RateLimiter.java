package main.com.chat.wechat.common.ratelimit;

public interface RateLimiter {
	boolean tryConsume(String bucketName, String key, RateLimitProperties.Limit limit);
}
