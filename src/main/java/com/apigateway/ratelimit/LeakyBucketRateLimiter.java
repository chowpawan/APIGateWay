package com.apigateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaky Bucket Rate Limiter with distributed state management using Redis
 * Provides smooth request rate limiting by "leaking" requests at a constant rate
 */
@Slf4j
@Component
public class LeakyBucketRateLimiter implements RateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Boolean> leakyBucketScript;

    @Value("${ratelimit.leaky-bucket.capacity:1000}")
    private long capacity;

    @Value("${ratelimit.leaky-bucket.leak-rate:100}")
    private long leakRate; // requests per second

    public LeakyBucketRateLimiter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // Lua script for atomic leaky bucket operation
        this.leakyBucketScript = RedisScript.of(
                "local key = KEYS[1]\n" +
                "local capacity = tonumber(ARGV[1])\n" +
                "local leak_rate = tonumber(ARGV[2])\n" +
                "local now = tonumber(ARGV[3])\n" +
                "\n" +
                "local bucket = redis.call('HGETALL', key)\n" +
                "local water_level = 0\n" +
                "local last_leak_time = now\n" +
                "\n" +
                "if #bucket > 0 then\n" +
                "  water_level = tonumber(bucket[2]) or 0\n" +
                "  last_leak_time = tonumber(bucket[4]) or now\n" +
                "end\n" +
                "\n" +
                "local time_passed = (now - last_leak_time) / 1000\n" +
                "local water_leaked = math.floor(time_passed * leak_rate)\n" +
                "water_level = math.max(0, water_level - water_leaked)\n" +
                "\n" +
                "if water_level < capacity then\n" +
                "  water_level = water_level + 1\n" +
                "  redis.call('HSET', key, 'water_level', water_level, 'last_leak', now)\n" +
                "  redis.call('EXPIRE', key, 3600)\n" +
                "  return 1\n" +
                "else\n" +
                "  redis.call('HSET', key, 'water_level', water_level, 'last_leak', now)\n" +
                "  redis.call('EXPIRE', key, 3600)\n" +
                "  return 0\n" +
                "end\n",
                Boolean.class
        );
    }

    @Override
    public boolean allowRequest(String key) {
        try {
            String redisKey = "lb_" + key;
            Long now = System.currentTimeMillis();

            List<String> keys = new ArrayList<>();
            keys.add(redisKey);

            List<Object> args = new ArrayList<>();
            args.add(capacity);
            args.add(leakRate);
            args.add(now);

            Boolean allowed = redisTemplate.execute(
                    leakyBucketScript,
                    keys,
                    args.toArray()
            );

            return allowed != null && allowed;
        } catch (Exception e) {
            log.error("Error in leaky bucket rate limiting for key: {}", key, e);
            // Fail open - allow request if Redis fails
            return true;
        }
    }

    @Override
    public long getRemainingQuota(String key) {
        try {
            String redisKey = "lb_" + key;
            Object waterLevel = redisTemplate.opsForHash().get(redisKey, "water_level");
            long current = waterLevel != null ? Long.parseLong(waterLevel.toString()) : 0;
            return capacity - current;
        } catch (Exception e) {
            log.warn("Error getting remaining quota for key: {}", key, e);
            return capacity;
        }
    }

    @Override
    public long getResetTime(String key) {
        try {
            String redisKey = "lb_" + key;
            Long ttl = redisTemplate.getExpire(redisKey);
            return ttl != null && ttl > 0 ? ttl * 1000 : 0;
        } catch (Exception e) {
            log.warn("Error getting reset time for key: {}", key, e);
            return 0;
        }
    }
}
