package com.sentinel.decision_engine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MerchantRiskService {

    private static final String MERCHANT_KEY = "merchant:stats:";
    private static final long   TTL_DAYS     = 30;

    // Fraud rate thresholds
    private static final double HIGH_FRAUD_THRESHOLD   = 0.60; // +40 risk
    private static final double MEDIUM_FRAUD_THRESHOLD = 0.30; // +20 risk

    private final StringRedisTemplate redis;

    public MerchantRiskService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record MerchantRiskResult(
            int    addedRisk,
            double fraudRate,
            long   totalTxns,
            long   blockedTxns,
            String reason
    ) {}

    // ── Evaluate merchant risk before decision ────────────────────────────────
    public MerchantRiskResult evaluate(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return new MerchantRiskResult(0, 0.0, 0, 0, "no_merchant_id");
        }

        String key = MERCHANT_KEY + merchantId;
        String totalStr   = (String) redis.opsForHash().get(key, "total_txns");
        String blockedStr = (String) redis.opsForHash().get(key, "blocked_txns");

        long total   = totalStr   != null ? Long.parseLong(totalStr)   : 0L;
        long blocked = blockedStr != null ? Long.parseLong(blockedStr) : 0L;

        // need at least 10 transactions for meaningful rate
        if (total < 10) {
            return new MerchantRiskResult(0, 0.0, total, blocked, "insufficient_data");
        }

        double fraudRate = (double) blocked / total;
        int addedRisk;
        String reason;

        if (fraudRate >= HIGH_FRAUD_THRESHOLD) {
            addedRisk = 40;
            reason = String.format("merchant_high_fraud(%.0f%%)", fraudRate * 100);
        } else if (fraudRate >= MEDIUM_FRAUD_THRESHOLD) {
            addedRisk = 20;
            reason = String.format("merchant_elevated_fraud(%.0f%%)", fraudRate * 100);
        } else {
            addedRisk = 0;
            reason = "merchant_ok";
        }

        return new MerchantRiskResult(addedRisk, fraudRate, total, blocked, reason);
    }

    // ── Update merchant stats after decision ──────────────────────────────────
    public void recordDecision(String merchantId, String decision) {
        if (merchantId == null || merchantId.isBlank()) return;

        String key = MERCHANT_KEY + merchantId;

        redis.opsForHash().increment(key, "total_txns", 1);

        if ("BLOCK".equals(decision) || "REJECTED".equals(decision)) {
            redis.opsForHash().increment(key, "blocked_txns", 1);
        }

        // update fraud rate
        String totalStr   = (String) redis.opsForHash().get(key, "total_txns");
        String blockedStr = (String) redis.opsForHash().get(key, "blocked_txns");

        if (totalStr != null && blockedStr != null) {
            double rate = Double.parseDouble(blockedStr) / Double.parseDouble(totalStr);
            redis.opsForHash().put(key, "fraud_rate", String.format("%.4f", rate));
        }

        redis.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }
}