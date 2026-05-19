package com.sentinel.decision_engine.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class DecisionEvent {
    private String txId;
    private String userId;
    private String decision;
    private int riskScore;
    private int mlRiskScore;
    private List<String> rulesFired;
    private long latencyMs;
    private String timestamp;
    private boolean mlFallback;
    private int shadowRiskScore;
    private String shadowDecision;

    public static DecisionEvent from(String txId, String userId, String decision,
                                     int riskScore, int mlRiskScore,
                                     List<String> rulesFired, long latencyMs,
                                     boolean mlFallback,
                                     int shadowRiskScore, String shadowDecision) {
        return DecisionEvent.builder()
                .txId(txId)
                .userId(userId)
                .decision(decision)
                .riskScore(riskScore)
                .mlRiskScore(mlRiskScore)
                .rulesFired(rulesFired)
                .latencyMs(latencyMs)
                .timestamp(Instant.now().toString())
                .mlFallback(mlFallback)
                .shadowRiskScore(shadowRiskScore)
                .shadowDecision(shadowDecision)
                .build();
    }
}