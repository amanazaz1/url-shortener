package com.demo.springdemo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client token-bucket limiter for write endpoints (POST /api/**).
 * In-memory and single-instance: behind several replicas, move to Redis/gateway-level limiting.
 * Uses the socket address (not X-Forwarded-For) so clients cannot spoof their identity.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private static final class Bucket {
        double tokens;
        long lastNanos;
    }

    private final double capacity;
    private final double refillPerNano;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED_CLIENTS; // bound memory
        }
    };

    public RateLimitFilter(AppProperties props) {
        this.capacity = props.rateLimitPerMinute();
        this.refillPerNano = props.rateLimitPerMinute() / 60.0 / 1_000_000_000.0;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        double waitSeconds = acquire(request.getRemoteAddr());
        if (waitSeconds > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf((long) Math.ceil(waitSeconds)));
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"title\":\"Too Many Requests\",\"status\":429,"
                            + "\"code\":\"rate_limited\",\"detail\":\"Rate limit exceeded, retry later\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** @return 0 if allowed, otherwise seconds until a token is available. */
    private double acquire(String key) {
        synchronized (buckets) {
            long now = System.nanoTime();
            Bucket b = buckets.computeIfAbsent(key, k -> {
                Bucket nb = new Bucket();
                nb.tokens = capacity;
                nb.lastNanos = now;
                return nb;
            });
            b.tokens = Math.min(capacity, b.tokens + (now - b.lastNanos) * refillPerNano);
            b.lastNanos = now;
            if (b.tokens >= 1.0) {
                b.tokens -= 1.0;
                return 0;
            }
            return (1.0 - b.tokens) / (refillPerNano * 1_000_000_000.0);
        }
    }
}
