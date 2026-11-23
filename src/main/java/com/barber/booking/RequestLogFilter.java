package com.barber.booking;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestLogFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "requestId";
    static final String HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final String FMT = "http method={} path={} status={} ms={} ip={} rid={}";
    private static final byte[] SALT = new byte[16];

    static {
        new SecureRandom().nextBytes(SALT);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String path = req.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        String incoming = req.getHeader(HEADER);
        String id = incoming != null && VALID_ID.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        res.setHeader(HEADER, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = res.getStatus();
            String method = safe(req.getMethod(), 10);
            String path = safe(req.getRequestURI(), 200);
            String ip = hash(req.getRemoteAddr());
            if (status >= 500) {
                log.error(FMT, method, path, status, ms, ip, id);
            } else if (status == 429 || status == 403) {
                log.warn(FMT, method, path, status, ms, ip, id);
            } else {
                log.info(FMT, method, path, status, ms, ip, id);
            }
            MDC.remove(MDC_KEY);
        }
    }

    private static String safe(String s, int max) {
        if (s == null) return "-";
        String t = s.length() > max ? s.substring(0, max) : s;
        return t.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String hash(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SALT);
            byte[] d = md.digest((ip == null ? "" : ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
