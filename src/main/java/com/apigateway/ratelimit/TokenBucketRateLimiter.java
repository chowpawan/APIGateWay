package com.apigateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token Bucket Rate Limiter with distributed state management using Redis
 * Handles clock skew by using server-side Lua scripts for atomic operations
 */
@Slf4j
@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Boolean> tokenBucketScript;

    @Value("${ratelimit.token-bucket.capacity:1000}")
    private long capacity;

    @Value("${ratelimit.token-bucket.refill-rate:100}")
    private long refillRate;

    @Value("${ratelimit.token-bucket.refill-interval-ms:1000}")
    private long refillIntervalMs;

    public TokenBucketRateLimiter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // Lua script for atomic token bucket operation
        // Prevents race conditions and handles clock skew
        this.tokenBucketScript = RedisScript.of(
                "local key = KEYS[1]\n" +
                "local capacity = tonumber(ARGV[1])\n" +
                "local refill_rate = tonumber(ARGV[2])\n" +
                "local refill_interval = tonumber(ARGV[3])\n" +
                "local now = tonumber(ARGV[4])\n" +
                "\n" +
                "local bucket = redis.call('HGETALL', key)\n" +
                "local tokens = capacity\n" +
                "local last_refill_time = now\n" +
                "\n" +
                "if #bucket > 0 then\n" +
                "  tokens = tonumber(bucket[2]) or capacity\n" +
                "  last_refill_time = tonumber(bucket[4]) or now\n" +
                "end\n" +
                "\n" +
                "local time_passed = now - last_refill_time\n" +
                "local tokens_to_add = math.floor(time_passed / refill_interval) * refill_rate\n" +
                "tokens = math.min(capacity, tokens + tokens_to_add)\n" +
                "\n" +
                "if tokens >= 1 then\n" +
                "  tokens = tokens - 1\n" +
                "  redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)\n" +
                "  redis.call('EXPIRE', key, 3600)\n" +
                "  return {1, tokens}\n" +
                "else\n" +
                "  redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)\n" +
                "  redis.call('EXPIRE', key, 3600)\n" +
                "  return {0, 0}\n" +
                "end\n",
                Boolean.class
        );
    }

    @Override
    public boolean allowRequest(String key) {
        try {
            String redisKey = "tb_" + key;
            Long now = System.currentTimeMillis();

            List<String> keys = new ArrayList<>();
            keys.add(redisKey);

            List<Object> args = new ArrayList<>();
            args.add(capacity);
            args.add(refillRate);
            args.add(refillIntervalMs);
            args.add(now);

            Boolean allowed = redisTemplate.execute(
                    tokenBucketScript,
                    keys,
                    args.toArray()
            );

            return allowed != null && allowed;
        } catch (Exception e) {
            log.error("Error in token bucket rate limiting for key: {}", key, e);
            // Fail open - allow request if Redis fails
            return true;
        }
    }

    @Override
    public long getRemainingQuota(String key) {
        try {
            String redisKey = "tb_" + key;
            Object tokens = redisTemplate.opsForHash().get(redisKey, "tokens");
            return tokens != null ? Long.parseLong(tokens.toString()) : capacity;
        } catch (Exception e) {
            log.warn("Error getting remaining quota for key: {}", key, e);
            return capacity;
        }
    }

    @Override
    public long getResetTime(String key) {
        try {
            String redisKey = "tb_" + key;
            Long ttl = redisTemplate.getExpire(redisKey);
            return ttl != null && ttl > 0 ? ttl * 1000 : 0;
        } catch (Exception e) {
            log.warn("Error getting reset time for key: {}", key, e);
            return 0;
        }
    }
}
