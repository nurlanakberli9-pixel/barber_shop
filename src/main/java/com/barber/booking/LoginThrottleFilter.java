package com.barber.booking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginAttemptService attempts;

    public LoginThrottleFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    static String key(HttpServletRequest req) {
        String u = req.getParameter("username");
        u = u == null ? "" : u.trim().toLowerCase(Locale.ROOT);
        if (u.length() > 64) u = u.substring(0, 64);
        return req.getRemoteAddr() + "|" + u;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(req.getMethod()) && "/api/login".equals(req.getRequestURI())
                && attempts.isBlocked(key(req))) {
            res.setStatus(429);
            return;
        }
        chain.doFilter(req, res);
    }
}
