package com.apigateway.ratelimit;

/**
 * Interface for rate limiting implementations
 */
public interface RateLimiter {
    /**
     * Check if a request is allowed based on the rate limiting algorithm
     * @param key unique identifier (IP, API key, user ID, etc.)
     * @return true if request is allowed, false if rate limit is exceeded
     */
    boolean allowRequest(String key);

    /**
     * Get the remaining quota for a key
     * @param key unique identifier
     * @return remaining requests allowed
     */
    long getRemainingQuota(String key);

    /**
     * Get the time until quota reset in milliseconds
     * @param key unique identifier
     * @return milliseconds until reset, 0 if no reset scheduled
     */
    long getResetTime(String key);
}
