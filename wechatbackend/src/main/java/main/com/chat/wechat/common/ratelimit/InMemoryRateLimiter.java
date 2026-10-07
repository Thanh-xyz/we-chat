package main.com.chat.wechat.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local/test implementation. The access-order map is intentionally bounded
 * and entries expire after the configured refill interval.
 */
public class InMemoryRateLimiter implements RateLimiter {
	private static final int DEFAULT_MAX_ENTRIES = 10_000;
	private final int maxEntries;
	private final Map<String, BucketEntry> buckets;
	private final RateLimitMetrics metrics;

	public InMemoryRateLimiter() {
		this(DEFAULT_MAX_ENTRIES, null);
	}

	public InMemoryRateLimiter(int maxEntries, MeterRegistry meterRegistry) {
		this.maxEntries = Math.max(100, maxEntries);
		this.buckets = new LinkedHashMap<>(Math.min(this.maxEntries, 256), 0.75f, true);
		this.metrics = meterRegistry == null ? null : new RateLimitMetrics(meterRegistry);
	}

	@Override
	public synchronized boolean tryConsume(
			String operation,
			RateLimitKey key,
			RateLimitProperties.Limit limit) {
		long now = System.currentTimeMillis();
		removeExpired(now);
		String bucketKey = operation + ":" + key.value();
		BucketEntry entry = buckets.get(bucketKey);
		if (entry == null) {
			if (buckets.size() >= maxEntries) {
				Iterator<String> iterator = buckets.keySet().iterator();
				if (iterator.hasNext()) {
					iterator.next();
					iterator.remove();
				}
			}
			entry = new BucketEntry(newBucket(limit), now, refillMillis(limit));
			buckets.put(bucketKey, entry);
		} else {
			entry.lastAccessMillis = now;
		}
		boolean allowed = entry.bucket.tryConsume(1);
		if (metrics != null) {
			metrics.decision(operation, allowed, "local");
		}
		return allowed;
	}

	int size() {
		synchronized (this) {
			removeExpired(System.currentTimeMillis());
			return buckets.size();
		}
	}

	private void removeExpired(long now) {
		Iterator<Map.Entry<String, BucketEntry>> iterator = buckets.entrySet().iterator();
		while (iterator.hasNext()) {
			BucketEntry entry = iterator.next().getValue();
			if (now - entry.lastAccessMillis >= entry.ttlMillis) {
				iterator.remove();
			}
		}
	}

	private Bucket newBucket(RateLimitProperties.Limit limit) {
		Bandwidth bandwidth = Bandwidth.classic(
				limit.capacity(),
				Refill.intervally(limit.capacity(), Duration.ofMinutes(limit.refillMinutes())));
		return Bucket.builder()
				.addLimit(bandwidth)
				.build();
	}

	private long refillMillis(RateLimitProperties.Limit limit) {
		try {
			return Math.max(1_000L, Math.multiplyExact(limit.refillMinutes(), 60_000L));
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}

	private static final class BucketEntry {
		private final Bucket bucket;
		private long lastAccessMillis;
		private final long ttlMillis;

		private BucketEntry(Bucket bucket, long lastAccessMillis, long ttlMillis) {
			this.bucket = bucket;
			this.lastAccessMillis = lastAccessMillis;
			this.ttlMillis = ttlMillis;
		}
	}
}
