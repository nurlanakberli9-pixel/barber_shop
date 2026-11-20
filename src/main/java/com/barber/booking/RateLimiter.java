package com.barber.booking;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private static final int MAX_KEYS = 50_000;

    private record Window(long start, long windowMs, int count) {}

    private final boolean enabled;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public long check(String key, int limit, long windowMs) {
        return check(key, limit, windowMs, System.currentTimeMillis());
    }

    long check(String key, int limit, long windowMs, long now) {
        if (!enabled) return 0;
        if (windows.size() > MAX_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= e.getValue().windowMs());
            if (windows.size() > MAX_KEYS && !windows.containsKey(key)) return 0;
        }
        long[] retry = {0};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.start() >= w.windowMs()) return new Window(now, windowMs, 1);
            if (w.count() >= limit) {
                retry[0] = Math.max(1, (w.start() + w.windowMs() - now + 999) / 1000);
                return w;
            }
            return new Window(w.start(), w.windowMs(), w.count() + 1);
        });
        return retry[0];
    }
}
