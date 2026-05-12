package com.sentinel.decision_engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.decision_engine.grpc.SentryMlClient;
import com.sentinel.decision_engine.kafka.DecisionProducer;
import com.sentinel.decision_engine.model.DecisionEvent;
import com.sentinel.decision_engine.service.RuleEngineService;
import com.sentinel.decision_engine.service.UserHistoryService;
import com.sentinel.decision_engine.service.VelocityService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class CheckController {

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

    public CheckController(UserHistoryService userHistoryService,
                           RuleEngineService ruleEngineService,
                           VelocityService velocityService,
                           SentryMlClient sentryMlClient,
                           DecisionProducer decisionProducer,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.userHistoryService = userHistoryService;
        this.ruleEngineService = ruleEngineService;
        this.velocityService = velocityService;
        this.sentryMlClient = sentryMlClient;
        this.decisionProducer = decisionProducer;
        this.objectMapper = objectMapper;
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
            @RequestBody Map<String, Object> request) throws Exception {

        long startTime = System.currentTimeMillis();
        String txId   = (String) request.getOrDefault("transaction_id", "unknown");
        String userId = (String) request.getOrDefault("user_id", "unknown");
        double amount = Double.parseDouble(
                request.getOrDefault("amount", 0).toString());

        // idempotency check
        String cached = userHistoryService.getIdempotentResponse(txId);
        if (cached != null) {
            Map<String, Object> cachedResponse =
                    objectMapper.readValue(cached, Map.class);
            return ResponseEntity.ok()
                    .header("X-Idempotency-Hit", "true")
                    .body(cachedResponse);
        }

        // user history
        List<String> history = userHistoryService.getRecentTransactions(userId);

        // velocity check
        VelocityService.VelocityResult velocity =
                velocityService.check(userId, amount);
        if (velocity.isTriggered()) {
            velocityCounter.increment();
        }

        // ML service
        boolean mlFallback = false;
        int mlRiskScore;
        try {
            mlRiskScore = sentryMlClient.score(txId, userId, amount, history.size());
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            System.err.println("Circuit OPEN — skipping ML for tx=" + txId);
            mlRiskScore = 50;
            mlFallback = true;
            mlFallbackCounter.increment();
        } catch (Exception e) {
            System.err.println("ML failed for tx=" + txId + ": " + e.getMessage());
            e.printStackTrace();  // ADD THIS
            mlRiskScore = 50;
            mlFallback = true;
            mlFallbackCounter.increment();
        }

        // rule engine
        request.put("ml_risk_score", mlRiskScore);
        RuleEngineService.RuleResult result = ruleEngineService.evaluate(request);

        // combine all risk signals
        int finalRisk = Math.min(100,
                mlRiskScore + result.riskScore() + velocity.addedRisk());

        // determine final decision
        String finalDecision;
        if (finalRisk >= 80) {
            finalDecision = "BLOCK";
        } else if (finalRisk >= 50) {
            finalDecision = "REVIEW";
        } else {
            finalDecision = "ALLOW";
        }

        // build rules fired list including velocity
        List<String> allRulesFired = new ArrayList<>(result.rulesFired());
        if (velocity.isTriggered()) {
            allRulesFired.add(velocity.reason());
        }

        // metrics
        if ("BLOCK".equals(finalDecision)) {
            blockedCounter.increment();
        } else {
            allowedCounter.increment();
        }

        long latency = System.currentTimeMillis() - startTime;
        decisionTimer.record(latency, java.util.concurrent.TimeUnit.MILLISECONDS);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("transaction_id", txId);
        response.put("user_id", userId);
        response.put("decision", finalDecision);
        response.put("risk_score", finalRisk);
        response.put("ml_risk_score", mlRiskScore);
        response.put("velocity_risk", velocity.addedRisk());
        response.put("rules_fired", allRulesFired);
        response.put("history_count", history.size());
        response.put("txn_count_60s", velocity.count60s());
        response.put("txn_count_1h", velocity.countHour());
        response.put("processing_time_ms", latency);

        userHistoryService.saveIdempotentResponse(txId,
                objectMapper.writeValueAsString(response));
        userHistoryService.addTransaction(userId,
                objectMapper.writeValueAsString(request));

        boolean finalMlFallback = mlFallback;
        DecisionEvent event = DecisionEvent.from(txId, userId, finalDecision,
                finalRisk, mlRiskScore, allRulesFired, latency, finalMlFallback);
        decisionProducer.publishDecision(event);

        return ResponseEntity.ok(response);
    }
}