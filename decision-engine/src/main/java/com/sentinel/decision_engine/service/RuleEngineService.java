package com.sentinel.decision_engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.decision_engine.model.Rule;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RuleEngineService {

    // volatile guarantees that pointer updates made by the Redis sync thread
    // are instantly visible to all incoming transaction threads without locking.
    private volatile List<Rule> rules = new ArrayList<>();

    private final ObjectMapper objectMapper;
    private static final String RULES_REDIS_KEY = "sentinel:rules";
    private final StringRedisTemplate redisTemplate;

    public RuleEngineService(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    // Single initialization entry point to eliminate execution race conditions
    @PostConstruct
    public void init() throws Exception {
        loadRules();
        pushRulesToRedis();
    }

    private void loadRules() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/rules.json")) {
            if (is == null) {
                throw new IllegalStateException("rules.json file not found in classpath");
            }
            Rule[] rulesArray = objectMapper.readValue(is, Rule[].class);
            // Use List.of to create an unmodifiable, thread-safe snapshot
            this.rules = List.of(rulesArray);
            System.out.println("Loaded " + rules.size() + " rules from file.");
        }
    }

    public RuleResult evaluate(Map<String, Object> request) {
        int riskScore = 0;
        String decision = "ALLOW";
        List<String> rulesFired = new ArrayList<>();

        // Local variable assignment keeps the snapshot uniform throughout the loop
        // even if another thread updates the class-level 'rules' pointer mid-execution.
        List<Rule> currentRules = this.rules;

        for (Rule rule : currentRules) {
            if (matches(rule, request)) {
                riskScore += rule.getAddRisk();
                rulesFired.add(rule.getName());

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

        String operator = rule.getOperator().toLowerCase();

        try {
            BigDecimal fieldValue = new BigDecimal(raw.toString());
            BigDecimal ruleValue = BigDecimal.valueOf(rule.getValue());

            return switch (operator) {
                case "gt"  -> fieldValue.compareTo(ruleValue) > 0;
                case "lt"  -> fieldValue.compareTo(ruleValue) < 0;
                case "eq"  -> fieldValue.compareTo(ruleValue) == 0;
                case "gte" -> fieldValue.compareTo(ruleValue) >= 0;
                case "lte" -> fieldValue.compareTo(ruleValue) <= 0;
                default    -> false;
            };
        } catch (NumberFormatException e) {
            // Fallback strategy handling dynamic string comparisons gracefully
            if ("eq".equals(operator)) {
                return raw.toString().equalsIgnoreCase(String.valueOf(rule.getValue()));
            }
            return false;
        }
    }

    public void onRulesUpdate(String message) {
        try {
            String rulesJson = redisTemplate.opsForValue().get(RULES_REDIS_KEY);
            if (rulesJson != null) {
                List<Rule> newRules = objectMapper.readValue(rulesJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Rule.class));

                // Atomically update pointer to an immutable structure copy
                this.rules = List.copyOf(newRules);
                System.out.println("Rules hot-reloaded: " + rules.size() + " rules. Trigger: " + message);
            }
        } catch (Exception e) {
            System.err.println("Failed to reload rules cleanly: " + e.getMessage());
        }
    }

    private void pushRulesToRedis() {
        try {
            String rulesJson = objectMapper.writeValueAsString(rules);
            redisTemplate.opsForValue().set(RULES_REDIS_KEY, rulesJson);
            System.out.println("Rules pushed to Redis: " + rules.size() + " rules");
        } catch (Exception e) {
            System.err.println("Failed to push rules to Redis: " + e.getMessage());
        }
    }

    public record RuleResult(String decision, int riskScore, List<String> rulesFired) {}
}