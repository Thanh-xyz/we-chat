package main.com.chat.wechat.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryRateLimiter implements RateLimiter {
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	@Override
	public boolean tryConsume(String bucketName, String key, RateLimitProperties.Limit limit) {
		String bucketKey = bucketName + ":" + key;
		Bucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> newBucket(limit));
		return bucket.tryConsume(1);
	}

	private Bucket newBucket(RateLimitProperties.Limit limit) {
		Bandwidth bandwidth = Bandwidth.classic(
				limit.capacity(),
				Refill.intervally(limit.capacity(), Duration.ofMinutes(limit.refillMinutes())));
		return Bucket.builder()
				.addLimit(bandwidth)
				.build();
	}
}
