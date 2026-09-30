package com.larv.pharmacy.auth;

import com.larv.pharmacy.common.exception.RateLimitedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sliding-window rate limiter for the login endpoint.
 * Limits each {@code ip|username} combination to {@value #MAX_ATTEMPTS}
 * attempts per {@value #WINDOW_MINUTES} minute window.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MINUTES = 5;
    private static final long WINDOW_MS = WINDOW_MINUTES * 60_000L;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String ip, String username) {
        String key = (ip == null ? "-" : ip) + "|" + (username == null ? "-" : username.toLowerCase(Locale.ROOT));
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs > WINDOW_MS) {
                Window fresh = new Window();
                fresh.startMs = now;
                return fresh;
            }
            return existing;
        });
        long attempts = window.count.incrementAndGet();
        if (attempts > MAX_ATTEMPTS) {
            long retrySeconds = Math.max(1, (window.startMs + WINDOW_MS - now) / 1000);
            throw new RateLimitedException(
                    "Too many login attempts. Try again in " + retrySeconds + " seconds");
        }
    }

    /** Clears the window after a successful login. */
    public void success(String ip, String username) {
        String key = (ip == null ? "-" : ip) + "|" + (username == null ? "-" : username.toLowerCase(Locale.ROOT));
        windows.remove(key);
    }

    @Scheduled(fixedDelayString = "PT10M")
    public void purge() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().startMs > WINDOW_MS);
    }

    private static final class Window {
        volatile long startMs;
        final AtomicLong count = new AtomicLong();
    }
}
