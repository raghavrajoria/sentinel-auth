package com.sentinel.decision_engine.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class VelocityService {

    private final RedisTemplate<String, String> redisTemplate;

    // thresholds
    private static final int MAX_TXN_PER_60S = 5;
    private static final int MAX_TXN_PER_1H  = 20;
    private static final int MAX_AMOUNT_PER_1H = 50000;

    public VelocityService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public VelocityResult check(String userId, double amount) {
        long now = System.currentTimeMillis();
        long window60s = now / 60000;       // 1-minute bucket
        long windowHour = now / 3600000;    // 1-hour bucket

        // --- transaction count per 60 seconds ---
        String key60s = "vel:count:60s:" + userId + ":" + window60s;
        Long count60s = redisTemplate.opsForValue().increment(key60s);
        if (count60s == 1) {
            redisTemplate.expire(key60s, Duration.ofSeconds(120));
        }

        // --- transaction count per hour ---
        String keyHour = "vel:count:1h:" + userId + ":" + windowHour;
        Long countHour = redisTemplate.opsForValue().increment(keyHour);
        if (countHour == 1) {
            redisTemplate.expire(keyHour, Duration.ofSeconds(7200));
        }

        // --- amount sum per hour ---
        String keyAmount = "vel:amount:1h:" + userId + ":" + windowHour;
        redisTemplate.opsForValue().increment(keyAmount, (long) amount);
        if (countHour == 1) {
            redisTemplate.expire(keyAmount, Duration.ofSeconds(7200));
        }
        Long totalAmount = Long.parseLong(
                redisTemplate.opsForValue().get(keyAmount) != null
                        ? redisTemplate.opsForValue().get(keyAmount) : "0"
        );

        // --- evaluate ---
        boolean highVelocity60s  = count60s  > MAX_TXN_PER_60S;
        boolean highVelocityHour = countHour > MAX_TXN_PER_1H;
        boolean highAmountHour   = totalAmount > MAX_AMOUNT_PER_1H;

        int addedRisk = 0;
        String reason = null;

        if (highVelocity60s) {
            addedRisk += 50;
            reason = "HIGH_VELOCITY_60S: " + count60s + " txns in 60s";
        } else if (highVelocityHour) {
            addedRisk += 30;
            reason = "HIGH_VELOCITY_1H: " + countHour + " txns in 1h";
        } else if (highAmountHour) {
            addedRisk += 20;
            reason = "HIGH_AMOUNT_1H: ₹" + totalAmount + " in 1h";
        }

        return new VelocityResult(addedRisk, reason,
                count60s, countHour, totalAmount);
    }

    public record VelocityResult(
            int addedRisk,
            String reason,
            long count60s,
            long countHour,
            long totalAmountHour
    ) {
        public boolean isTriggered() { return addedRisk > 0; }
    }
}