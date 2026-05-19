package com.sentinel.decision_engine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

@Service
public class UserBaselineService {

    private static final String BASELINE_KEY  = "user:baseline:";
    private static final String HOURS_KEY     = "user:hours:";
    private static final String DEVICES_KEY   = "user:devices:";
    private static final long   TTL_DAYS      = 30;

    private final StringRedisTemplate redis;

    public UserBaselineService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ── Baseline result returned to CheckController ──────────────────────────

    public record BaselineResult(
            int     deviationRisk,   // risk score added (0–40)
            boolean isNewDevice,
            boolean isUnusualHour,
            boolean isAmountDeviation,
            double  userAvgAmount,
            String  reason
    ) {}

    // ── Main entry point ─────────────────────────────────────────────────────

    public BaselineResult evaluate(String userId, double amount,
                                   String deviceId, int hourOfDay) {

        String baselineKey = BASELINE_KEY + userId;
        String hoursKey    = HOURS_KEY    + userId;
        String devicesKey  = DEVICES_KEY  + userId;

        // fetch current baseline
        String avgStr   = redis.opsForHash().get(baselineKey, "avg_amount")  != null
                ? (String) redis.opsForHash().get(baselineKey, "avg_amount") : null;
        String countStr = redis.opsForHash().get(baselineKey, "tx_count")    != null
                ? (String) redis.opsForHash().get(baselineKey, "tx_count")   : null;

        double userAvg   = avgStr   != null ? Double.parseDouble(avgStr)   : 0.0;
        long   txCount   = countStr != null ? Long.parseLong(countStr)     : 0L;

        // ── 1. First-time user → no baseline yet, low risk, just record ──────
        if (txCount == 0) {
            updateBaseline(userId, amount, deviceId, hourOfDay);
            return new BaselineResult(0, false, false, false, 0.0, "new_user");
        }

        int     risk            = 0;
        boolean isAmountDev     = false;
        boolean isUnusualHour   = false;
        boolean isNewDevice     = false;
        StringBuilder reason    = new StringBuilder();

        // ── 2. Amount deviation: current > 5× user average ───────────────────
        if (userAvg > 0 && amount > 5 * userAvg) {
            isAmountDev = true;
            risk += 30;
            reason.append("amount_deviation(").append(String.format("%.0f", amount))
                    .append("x avg ").append(String.format("%.0f", userAvg)).append(") ");
        } else if (userAvg > 0 && amount > 3 * userAvg) {
            risk += 15;
            reason.append("amount_elevated ");
        }

        // ── 3. Unusual hour: not in user's normal hours set ───────────────────
        Boolean hourKnown = redis.opsForSet().isMember(hoursKey, String.valueOf(hourOfDay));
        if (Boolean.FALSE.equals(hourKnown)) {
            isUnusualHour = true;
            risk += 10;
            reason.append("unusual_hour(").append(hourOfDay).append("h) ");
        }

        // ── 4. New device ─────────────────────────────────────────────────────
        if (deviceId != null && !deviceId.isBlank()) {
            Boolean trusted = redis.opsForSet().isMember(devicesKey, deviceId);
            if (Boolean.FALSE.equals(trusted)) {
                isNewDevice = true;
                risk += 15;
                reason.append("new_device ");
            }
        }

        risk = Math.min(risk, 40); // cap baseline contribution

        // update baseline after evaluation
        updateBaseline(userId, amount, deviceId, hourOfDay);

        return new BaselineResult(
                risk, isNewDevice, isUnusualHour, isAmountDev,
                userAvg, reason.toString().trim()
        );
    }

    // ── Update baseline in Redis ──────────────────────────────────────────────

    private void updateBaseline(String userId, double amount,
                                String deviceId, int hourOfDay) {

        String baselineKey = BASELINE_KEY + userId;
        String hoursKey    = HOURS_KEY    + userId;
        String devicesKey  = DEVICES_KEY  + userId;

        // running average: new_avg = (old_avg * count + amount) / (count + 1)
        String avgStr   = (String) redis.opsForHash().get(baselineKey, "avg_amount");
        String countStr = (String) redis.opsForHash().get(baselineKey, "tx_count");

        double oldAvg = avgStr   != null ? Double.parseDouble(avgStr)   : 0.0;
        long   count  = countStr != null ? Long.parseLong(countStr)     : 0L;

        double newAvg = (oldAvg * count + amount) / (count + 1);

        redis.opsForHash().put(baselineKey, "avg_amount", String.format("%.2f", newAvg));
        redis.opsForHash().put(baselineKey, "tx_count",   String.valueOf(count + 1));
        redis.expire(baselineKey, TTL_DAYS, TimeUnit.DAYS);

        // record this hour as a known hour
        redis.opsForSet().add(hoursKey, String.valueOf(hourOfDay));
        redis.expire(hoursKey, TTL_DAYS, TimeUnit.DAYS);

        // record device as trusted (after this transaction)
        if (deviceId != null && !deviceId.isBlank()) {
            redis.opsForSet().add(devicesKey, deviceId);
            redis.expire(devicesKey, TTL_DAYS, TimeUnit.DAYS);
        }
    }
}