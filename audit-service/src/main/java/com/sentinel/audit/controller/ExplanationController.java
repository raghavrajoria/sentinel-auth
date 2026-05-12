package com.sentinel.audit.controller;

import com.sentinel.audit.model.Decision;
import com.sentinel.audit.repository.DecisionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ExplanationController {

    private final DecisionRepository decisionRepository;

    public ExplanationController(DecisionRepository decisionRepository) {
        this.decisionRepository = decisionRepository;
    }

    @GetMapping("/explanation/{txId}")
    public ResponseEntity<?> explain(@PathVariable String txId) {
        return decisionRepository.findById(txId)
                .map(d -> ResponseEntity.ok(Map.of(
                        "tx_id", d.getTxId(),
                        "user_id", d.getUserId(),
                        "decision", d.getDecision(),
                        "risk_score", d.getRiskScore(),
                        "ml_risk_score", d.getMlRiskScore(),
                        "rules_fired", d.getRulesFired(),
                        "latency_ms", d.getLatencyMs(),
                        "ml_fallback", d.isMlFallback(),
                        "created_at", d.getCreatedAt().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}