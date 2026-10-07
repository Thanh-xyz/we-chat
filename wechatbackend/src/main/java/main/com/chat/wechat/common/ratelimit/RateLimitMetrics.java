package main.com.chat.wechat.common.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

final class RateLimitMetrics {
	private final MeterRegistry meterRegistry;

	RateLimitMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	void decision(String operation, boolean allowed, String backend) {
		Counter.builder("rate_limit_" + (allowed ? "allowed" : "rejected") + "_total")
				.tag("operation", safeOperation(operation))
				.tag("result", allowed ? "allowed" : "rejected")
				.tag("backend", backend)
				.register(meterRegistry)
				.increment();
	}

	void error(String operation, String kind) {
		Counter.builder("rate_limit_errors_total")
				.tag("operation", safeOperation(operation))
				.tag("result", kind)
				.register(meterRegistry)
				.increment();
	}

	void redisFailure(String operation) {
		Counter.builder("rate_limit_redis_failures_total")
				.tag("operation", safeOperation(operation))
				.tag("result", "redis_unavailable")
				.register(meterRegistry)
				.increment();
	}

	void fallback(String operation) {
		Counter.builder("rate_limit_fallback_total")
				.tag("operation", safeOperation(operation))
				.tag("result", "bounded_local")
				.register(meterRegistry)
				.increment();
	}

	private String safeOperation(String operation) {
		return operation == null || operation.isBlank() ? "unknown" : operation;
	}
}
