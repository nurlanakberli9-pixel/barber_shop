package com.barber.booking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, String method, Pattern path, int limit, long windowMs) {
        boolean matches(String m, String p) {
            return (method == null || method.equals(m)) && path.matcher(p).matches();
        }
    }

    private static final long MIN = 60_000L;
    private static final long QUARTER = 15 * MIN;
    private static final long HOUR = 60 * MIN;
    private static final long MAX_BODY = 16 * 1024;
    private static final Pattern BOOKING = Pattern.compile("^/api/shops/\\d+/bookings$");

    private static final List<Rule> RULES = List.of(
            new Rule("book-min", "POST", BOOKING, 5, MIN),
            new Rule("book-hour", "POST", BOOKING, 20, HOUR),
            new Rule("login", "POST", Pattern.compile("^/api/login$"), 20, QUARTER),
            new Rule("admin", null, Pattern.compile("^/api/admin/.*$"), 120, 10 * MIN),
            new Rule("push-test", "POST", Pattern.compile("^/api/panel/push/test$"), 10, HOUR),
            new Rule("password", "POST", Pattern.compile("^/api/panel/password$"), 10, HOUR),
            new Rule("public-read", "GET", Pattern.compile("^/api/(shops(/.*)?|csrf)$"), 240, MIN),
            new Rule("health", "GET", Pattern.compile("^/actuator/health(/.*)?$"), 120, MIN));

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();
        if (path != null && (path.startsWith("/api/") || path.startsWith("/actuator/"))) {
            String method = req.getMethod();
            if (("POST".equals(method) || "PUT".equals(method)) && req.getContentLengthLong() > MAX_BODY) {
                res.setStatus(413);
                return;
            }
            String ip = req.getRemoteAddr();
            long retry = 0;
            for (Rule r : RULES) {
                if (!r.matches(method, path)) continue;
                retry = Math.max(retry, limiter.check(r.name() + "|" + ip, r.limit(), r.windowMs()));
            }
            if (retry > 0) {
                res.setStatus(429);
                res.setHeader("Retry-After", String.valueOf(retry));
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
