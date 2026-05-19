package com.sentinel.decision_engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.decision_engine.grpc.SentryMlClient;
import com.sentinel.decision_engine.grpc.ShadowMlClient;
import com.sentinel.decision_engine.kafka.DecisionProducer;
import com.sentinel.decision_engine.model.DecisionEvent;
import com.sentinel.decision_engine.service.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class CheckController {

    // Defined native Logger instance to handle diagnostic outputs securely
    private static final Logger log = LoggerFactory.getLogger(CheckController.class);

    private final UserHistoryService userHistoryService;
    private final RuleEngineService ruleEngineService;
    private final VelocityService velocityService;
    private final SentryMlClient sentryMlClient;
    private final DecisionProducer decisionProducer;
    private final ObjectMapper objectMapper;

    private final Counter blockedCounter;
    private final Counter allowedCounter;
    private final Counter mlFallbackCounter;
    private final Counter velocityCounter;
    private final Timer decisionTimer;
    private final ShadowMlClient shadowMlClient;

    private final UserBaselineService userBaselineService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    private final MerchantRiskService merchantRiskService;

    public CheckController(
            UserHistoryService userHistoryService,
            RuleEngineService ruleEngineService,
            VelocityService velocityService,
            SentryMlClient sentryMlClient,
            DecisionProducer decisionProducer,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            UserBaselineService userBaselineService,
            OtpService otpService,
            EmailService emailService,
            ShadowMlClient shadowMlClient,
            StringRedisTemplate redisTemplate,
            MerchantRiskService merchantRiskService

    ) {
        this.userHistoryService = userHistoryService;
        this.ruleEngineService = ruleEngineService;
        this.velocityService = velocityService;
        this.sentryMlClient = sentryMlClient;
        this.shadowMlClient = shadowMlClient;
        this.decisionProducer = decisionProducer;
        this.objectMapper = objectMapper;
        this.userBaselineService = userBaselineService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
        this.merchantRiskService = merchantRiskService;

        // Metrics registration
        this.blockedCounter = Counter.builder("decisions_blocked_total")
                .register(meterRegistry);

        this.allowedCounter = Counter.builder("decisions_allowed_total")
                .register(meterRegistry);

        this.mlFallbackCounter = Counter.builder("decisions_ml_fallback_total")
                .register(meterRegistry);

        this.velocityCounter = Counter.builder("decisions_velocity_triggered_total")
                .description("Transactions flagged by velocity checks")
                .register(meterRegistry);

        this.decisionTimer = Timer.builder("decisions_latency_seconds")
                .register(meterRegistry);
    }

    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check(
            @RequestBody Map<String, Object> request
    ) throws Exception {

        long startTime = System.currentTimeMillis();

        // Extract request fields
        String txId = (String) request.getOrDefault("transaction_id", "unknown");
        String userId = (String) request.getOrDefault("user_id", "unknown");
        double amount = Double.parseDouble(request.getOrDefault("amount", 0).toString());

        /*
         * ===================================================
         * 1. Idempotency check
         * Prevent duplicate processing
         * ===================================================
         */
        String cached = userHistoryService.getIdempotentResponse(txId);

        if (cached != null) {
            Map<String, Object> cachedResponse = objectMapper.readValue(cached, Map.class);
            return ResponseEntity.ok()
                    .header("X-Idempotency-Hit", "true")
                    .body(cachedResponse);
        }

        /*
         * ===================================================
         * 2. Get transaction history
         * ===================================================
         */
        List<String> history = userHistoryService.getRecentTransactions(userId);

        /*
         * ===================================================
         * 3. Velocity checks
         * ===================================================
         */
        VelocityService.VelocityResult velocity = velocityService.check(userId, amount);

        if (velocity.isTriggered()) {
            velocityCounter.increment();
        }

        /*
         * ===================================================
         * 4. User behavior baseline
         * ===================================================
         */
        String deviceId = (String) request.getOrDefault("device_id", null);
        int hourOfDay = LocalDateTime.now().getHour();

        UserBaselineService.BaselineResult baseline =
                userBaselineService.evaluate(userId, amount, deviceId, hourOfDay);

        // merchant risk
        String merchantId = (String) request.getOrDefault("merchant_id", null);
        MerchantRiskService.MerchantRiskResult merchantRisk =
                merchantRiskService.evaluate(merchantId);

        /*
         * ===================================================
         * 5. ML risk scoring
         * Includes fallback behavior
         * ===================================================
         */
        boolean mlFallback = false;
        int mlRiskScore;

        try {
            mlRiskScore = sentryMlClient.score(txId, userId, amount, history.size());
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("Circuit OPEN: ML skipped for tx={}", txId);
            mlRiskScore = 50;
            mlFallback = true;
            mlFallbackCounter.increment();
        } catch (Exception e) {
            log.error("ML failed for tx={}: {}", txId, e.getMessage(), e);
            mlRiskScore = 50;
            mlFallback = true;
            mlFallbackCounter.increment();
        }

        /*
         * ===================================================
         * 6. Rule engine
         * ===================================================
         */
        request.put("ml_risk_score", mlRiskScore);

        RuleEngineService.RuleResult result = ruleEngineService.evaluate(request);

        /*
         * ===================================================
         * 7. Final risk calculation
         * ===================================================
         */
        int finalRisk = Math.min(100,
                mlRiskScore + result.riskScore() + velocity.addedRisk()
                        + baseline.deviationRisk() + merchantRisk.addedRisk());

        /*
         * ===================================================
         * 8. Core decision
         * ===================================================
         */
        String finalDecision;

        if (finalRisk >= 80) {
            finalDecision = "BLOCK";
        } else if (finalRisk >= 50) {
            finalDecision = "REVIEW";
        } else {
            finalDecision = "ALLOW";
        }

        /*
         * ===================================================
         * 8b. Shadow ML Pipeline Execution
         * Asynchronous shadow scoring and dark-launch verification
         * ===================================================
         */
        int shadowRiskScore = shadowMlClient.score(txId, userId, amount, history.size());
        String shadowDecision = "UNAVAILABLE";

        if (shadowRiskScore >= 0) {
            int shadowFinalRisk = Math.min(100,
                    shadowRiskScore + result.riskScore() + velocity.addedRisk() + baseline.deviationRisk());
            shadowDecision = shadowFinalRisk >= 80 ? "BLOCK" : shadowFinalRisk >= 50 ? "REVIEW" : "ALLOW";
        }

        // Evaluate model disagreements transparently
        if (shadowRiskScore >= 0 && !shadowDecision.equals(finalDecision)) {
            log.warn("SHADOW DISAGREES tx={} primary={} shadow={} primaryRisk={} shadowRisk={}",
                    txId, finalDecision, shadowDecision, finalRisk, shadowRiskScore);
        }
        // log to Redis for comparison
        if (shadowRiskScore >= 0) {
            String shadowKey = "shadow:scores:" + txId;
            redisTemplate.opsForHash().put(shadowKey, "primary_score",    String.valueOf(mlRiskScore));
            redisTemplate.opsForHash().put(shadowKey, "shadow_score",     String.valueOf(shadowRiskScore));
            redisTemplate.opsForHash().put(shadowKey, "primary_decision", finalDecision);
            redisTemplate.opsForHash().put(shadowKey, "shadow_decision",  shadowDecision);
            redisTemplate.opsForHash().put(shadowKey, "amount",           String.valueOf(amount));
            redisTemplate.expire(shadowKey, 7, java.util.concurrent.TimeUnit.DAYS);
            log.info("Shadow scores saved for tx={} primary={} shadow={}", txId, mlRiskScore, shadowRiskScore);
        }

        /*
         * ===================================================
         * 9. Initialize response
         * ===================================================
         */
        Map<String, Object> response = new HashMap<>();
        response.put("transaction_id", txId);
        response.put("user_id", userId);

        /*
         * ===================================================
         * 10. Step-up authentication
         * REVIEW -> PENDING_OTP
         * ===================================================
         */
        if ("REVIEW".equals(finalDecision)) {
            String userEmail = (String) request.getOrDefault("email", null);

            if (userEmail != null && !userEmail.isBlank()) {
                String otp = otpService.generateOtp(txId);
                otpService.storeEmail(txId, userEmail);
                emailService.sendOtp(userEmail, txId, otp);

                finalDecision = "PENDING_OTP";
                response.put("message", "OTP sent to registered email. Use POST /v1/verify-otp/" + txId);
            }
        }

        /*
         * ===================================================
         * 11. Collect triggered rules
         * ===================================================
         */
        List<String> allRulesFired = new ArrayList<>(result.rulesFired());

        if (baseline.deviationRisk() > 0) {
            allRulesFired.add("BASELINE:" + baseline.reason());
        }

        /*
         * ===================================================
         * 12. Metrics
         * Only count actual ALLOW/BLOCK
         * ===================================================
         */
        switch (finalDecision) {
            case "BLOCK":
                blockedCounter.increment();
                break;
            case "ALLOW":
                allowedCounter.increment();
                break;
        }

        /*
         * ===================================================
         * 13. Record processing time
         * ===================================================
         */
        long latency = System.currentTimeMillis() - startTime;

        decisionTimer.record(latency, java.util.concurrent.TimeUnit.MILLISECONDS);

        /*
         * ===================================================
         * 14. Build final response
         * ===================================================
         */
        response.put("decision", finalDecision);
        response.put("risk_score", finalRisk);
        response.put("ml_risk_score", mlRiskScore);
        response.put("velocity_risk", velocity.addedRisk());
        response.put("rules_fired", allRulesFired);
        response.put("history_count", history.size());
        response.put("txn_count_60s", velocity.count60s());
        response.put("txn_count_1h", velocity.countHour());
        response.put("processing_time_ms", latency);
        response.put("baseline_risk", baseline.deviationRisk());
        response.put("user_avg_amount", baseline.userAvgAmount());
        response.put("is_amount_deviation", baseline.isAmountDeviation());
        response.put("is_unusual_hour", baseline.isUnusualHour());
        response.put("is_new_device", baseline.isNewDevice());
        response.put("merchant_risk",      merchantRisk.addedRisk());
        response.put("merchant_fraud_rate", merchantRisk.fraudRate());
        response.put("merchant_id",        merchantId != null ? merchantId : "none");

        /*
         * ===================================================
         * 15. Store for future use
         * ===================================================
         */
        userHistoryService.saveIdempotentResponse(txId, objectMapper.writeValueAsString(response));
        userHistoryService.addTransaction(userId, objectMapper.writeValueAsString(request));

        /*
         * ===================================================
         * 16. Publish Kafka event
         * Includes fallback tracking and experimental shadow evaluation properties
         * ===================================================
         */
        DecisionEvent event = DecisionEvent.from(
                txId,
                userId,
                finalDecision,
                finalRisk,
                mlRiskScore,
                allRulesFired,
                latency,
                mlFallback,
                shadowRiskScore,
                shadowDecision
        );

        decisionProducer.publishDecision(event);
        merchantRiskService.recordDecision(merchantId, finalDecision);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/rules")
    public ResponseEntity<?> updateRules(@RequestBody List<Map<String, Object>> newRules) {
        try {
            // Save new rules to Redis
            String rulesJson = objectMapper.writeValueAsString(newRules);
            redisTemplate.opsForValue().set("sentinel:rules", rulesJson);

            // Publish update signal to all instances
            redisTemplate.convertAndSend("rules:updates", "manual-update-" + System.currentTimeMillis());

            return ResponseEntity.ok(Map.of(
                    "status", "rules updated",
                    "count", newRules.size(),
                    "message", "All instances will reload within 100ms"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}