package com.sentinel.decision_engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.decision_engine.model.Rule;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class RuleEngineService {

    private List<Rule> rules = new ArrayList<>();
    private final ObjectMapper objectMapper;

    public RuleEngineService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadRules() throws Exception {
        InputStream is = getClass().getResourceAsStream("/rules.json");
        rules = Arrays.asList(objectMapper.readValue(is, Rule[].class));
        System.out.println("Loaded " + rules.size() + " rules");
    }

    public RuleResult evaluate(Map<String, Object> request) {
        int riskScore = 0;
        String decision = "ALLOW";
        List<String> rulesFired = new ArrayList<>();

        for (Rule rule : rules) {
            if (matches(rule, request)) {
                riskScore += rule.getAddRisk();
                rulesFired.add(rule.getName());
                // BLOCK overrides REVIEW, REVIEW overrides ALLOW
                if ("BLOCK".equals(rule.getAction())) {
                    decision = "BLOCK";
                } else if ("REVIEW".equals(rule.getAction()) && !"BLOCK".equals(decision)) {
                    decision = "REVIEW";
                }
            }
        }

        return new RuleResult(decision, Math.min(riskScore, 100), rulesFired);
    }

    private boolean matches(Rule rule, Map<String, Object> request) {
        Object raw = request.get(rule.getField());
        if (raw == null) return false;

        double fieldValue = Double.parseDouble(raw.toString());

        return switch (rule.getOperator()) {
            case "gt" -> fieldValue > rule.getValue();
            case "lt" -> fieldValue < rule.getValue();
            case "eq" -> fieldValue == rule.getValue();
            case "gte" -> fieldValue >= rule.getValue();
            case "lte" -> fieldValue <= rule.getValue();
            default -> false;
        };
    }

    public record RuleResult(String decision, int riskScore, List<String> rulesFired) {}
}