package com.barber.booking;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final int MAX_KEYS = 10_000;

    private record State(int failures, long since) {}

    private final ConcurrentHashMap<String, State> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        State s = attempts.get(key);
        if (s == null) return false;
        if (System.currentTimeMillis() - s.since() > WINDOW_MS) {
            attempts.remove(key);
            return false;
        }
        return s.failures() >= MAX_FAILURES;
    }

    public void failed(String key) {
        long now = System.currentTimeMillis();
        if (attempts.size() > MAX_KEYS) {
            attempts.entrySet().removeIf(e -> now - e.getValue().since() > WINDOW_MS);
        }
        attempts.merge(key, new State(1, now), (old, fresh) ->
                now - old.since() > WINDOW_MS ? fresh : new State(old.failures() + 1, old.since()));
    }

    public void succeeded(String key) {
        attempts.remove(key);
    }
}
