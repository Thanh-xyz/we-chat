package main.com.chat.wechat.common.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryRateLimiter implements RateLimiter {
	private final Map<String, BucketState> buckets = new ConcurrentHashMap<>();

	@Override
	public boolean tryConsume(String bucketName, String key, RateLimitProperties.Limit limit) {
		String bucketKey = bucketName + ":" + key;
		BucketState bucket = buckets.computeIfAbsent(bucketKey, ignored -> new BucketState(limit.capacity(), Instant.now()));
		synchronized (bucket) {
			refill(bucket, limit);
			if (bucket.tokens <= 0) {
				return false;
			}
			bucket.tokens--;
			return true;
		}
	}

	private void refill(BucketState bucket, RateLimitProperties.Limit limit) {
		Instant now = Instant.now();
		Duration refillDuration = Duration.ofMinutes(limit.refillMinutes());
		if (Duration.between(bucket.lastRefill, now).compareTo(refillDuration) >= 0) {
			bucket.tokens = limit.capacity();
			bucket.lastRefill = now;
		}
	}

	private static class BucketState {
		private int tokens;
		private Instant lastRefill;

		private BucketState(int tokens, Instant lastRefill) {
			this.tokens = tokens;
			this.lastRefill = lastRefill;
		}
	}
}
