package com.queuetable.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuetable.shared.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "queuetable.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_JOINS_PER_HOUR = 10;
    private static final long WINDOW_MILLIS = 3_600_000L; // 1 hour

    private final Map<String, RateWindow> ipWindows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only rate limit POST to public queue join endpoints
        if ("POST".equals(method) && path.matches("/public/restaurants/.+/queue")) {
            String ip = getClientIp(request);
            RateWindow window = ipWindows.compute(ip, (key, existing) -> {
                if (existing == null || existing.isExpired()) {
                    return new RateWindow();
                }
                return existing;
            });

            if (window.incrementAndCheck() > MAX_JOINS_PER_HOUR) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorResponse error = ErrorResponse.of(
                        "Rate limit exceeded. Maximum " + MAX_JOINS_PER_HOUR + " joins per hour.",
                        "RATE_LIMITED", 429);
                response.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateWindow {
        private final Instant start = Instant.now();
        private final AtomicInteger count = new AtomicInteger(0);

        int incrementAndCheck() {
            return count.incrementAndGet();
        }

        boolean isExpired() {
            return Instant.now().toEpochMilli() - start.toEpochMilli() > WINDOW_MILLIS;
        }
    }
}
