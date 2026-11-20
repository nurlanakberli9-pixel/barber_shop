package com.barber.booking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; manifest-src 'self'; worker-src 'self'; "
            + "form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        res.setHeader("Content-Security-Policy", CSP);
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=(), payment=(), usb=()");
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        res.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        chain.doFilter(req, res);
    }
}
