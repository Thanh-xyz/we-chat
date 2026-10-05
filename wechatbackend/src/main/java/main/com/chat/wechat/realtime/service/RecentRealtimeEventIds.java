package main.com.chat.wechat.realtime.service;

import java.time.Instant;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RecentRealtimeEventIds {
	private static final Duration RETENTION = Duration.ofMinutes(10);
	private final int maxSize;
	private final Map<UUID, Instant> entries = new LinkedHashMap<>();

	public RecentRealtimeEventIds(int maxSize) {
		this.maxSize = maxSize;
	}

	public synchronized boolean markIfNew(UUID eventId) {
		Instant now = Instant.now();
		prune(now);
		if (entries.containsKey(eventId)) {
			return false;
		}
		entries.put(eventId, now);
		while (entries.size() > maxSize) {
			entries.remove(entries.keySet().iterator().next());
		}
		return true;
	}

	private void prune(Instant now) {
		Iterator<Map.Entry<UUID, Instant>> iterator = entries.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue().plus(RETENTION).isBefore(now)) {
				iterator.remove();
			}
		}
	}
}
