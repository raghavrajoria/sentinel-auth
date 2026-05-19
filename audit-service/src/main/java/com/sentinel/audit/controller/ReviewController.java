package com.sentinel.audit.controller;

import com.sentinel.audit.model.Decision;
import com.sentinel.audit.repository.DecisionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/review")
public class ReviewController {

    private final DecisionRepository decisionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ReviewController(DecisionRepository decisionRepository,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.decisionRepository = decisionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // GET /v1/review/queue — returns all REVIEW decisions pending analyst action
    @GetMapping("/queue")
    public ResponseEntity<List<Map<String, Object>>> queue() {
        List<Decision> reviews = decisionRepository
                .findTop50ByDecisionOrderByCreatedAtDesc("REVIEW");

        List<Map<String, Object>> result = reviews.stream()
                .map(d -> Map.<String, Object>of(
                        "tx_id",        d.getTxId(),
                        "user_id",      d.getUserId(),
                        "risk_score",   d.getRiskScore(),
                        "ml_risk_score",d.getMlRiskScore(),
                        "rules_fired",  d.getRulesFired(),
                        "created_at",   d.getCreatedAt().toString()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    // POST /v1/review/{txId} — analyst approves or rejects
    @PostMapping("/{txId}")
    public ResponseEntity<Map<String, Object>> review(
            @PathVariable String txId,
            @RequestBody Map<String, String> body) {

        String action = body.getOrDefault("action", "").toUpperCase();

        if (!action.equals("APPROVE") && !action.equals("REJECT")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "action must be APPROVE or REJECT"));
        }

        return decisionRepository.findById(txId).map(decision -> {

            String newDecision = action.equals("APPROVE") ? "APPROVED" : "REJECTED";
            decision.setDecision(newDecision);
            decisionRepository.save(decision);

            // publish to decisions.manual topic
            String analyst = body.getOrDefault("analyst", "unknown");
            String payload = String.format(
                    "{\"txId\":\"%s\",\"userId\":\"%s\",\"action\":\"%s\",\"analyst\":\"%s\",\"riskScore\":%d}",
                    txId, decision.getUserId(), newDecision, analyst, decision.getRiskScore()
            );
            kafkaTemplate.send("decisions.manual", txId, payload);

            return ResponseEntity.ok(Map.<String, Object>of(
                    "tx_id",    txId,
                    "decision", newDecision,
                    "analyst",  analyst,
                    "message",  "Decision recorded and published"
            ));

        }).orElse(ResponseEntity.notFound().build());
    }
}