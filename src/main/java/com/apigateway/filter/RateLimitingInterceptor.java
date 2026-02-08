package com.apigateway.filter;

import com.apigateway.ratelimit.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limiting interceptor using configurable rate limiter (Token Bucket or Leaky Bucket)
 */
@Slf4j
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    @Value("${ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${ratelimit.key-generator:ip}")
    private String keyGenerator; // ip, api-key, user-id

    public RateLimitingInterceptor(@Qualifier("tokenBucketRateLimiter") RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimitEnabled) {
            return true;
        }

        String limitKey = extractLimitKey(request);

        boolean allowed = rateLimiter.allowRequest(limitKey);

        // Add rate limit headers to response
        long remaining = rateLimiter.getRemainingQuota(limitKey);
        long resetTime = rateLimiter.getResetTime(limitKey);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));

        if (!allowed) {
            response.setStatus(429); // SC_TOO_MANY_REQUESTS
            response.setHeader("Retry-After", String.valueOf(resetTime / 1000));
            log.warn("Rate limit exceeded for key: {}", limitKey);
            return false;
        }

        return true;
    }

    private String extractLimitKey(HttpServletRequest request) {
        return switch (keyGenerator.toLowerCase()) {
            case "api-key" -> request.getHeader("X-API-Key") != null ?
                    request.getHeader("X-API-Key") :
                    request.getRemoteAddr();
            case "user-id" -> request.getHeader("X-User-ID") != null ?
                    request.getHeader("X-User-ID") :
                    request.getRemoteAddr();
            default -> request.getRemoteAddr();
        };
    }
}
