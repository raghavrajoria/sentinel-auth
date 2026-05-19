package com.sentinel.decision_engine.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/shadow")
public class ShadowController {

    private final RedisTemplate<String, String> redisTemplate;

    public ShadowController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/compare/{txId}")
    public ResponseEntity<Map<String, Object>> compare(@PathVariable String txId) {
        Map<Object, Object> entries = redisTemplate.opsForHash()
                .entries("shadow:scores:" + txId);

        if (entries.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = entries.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString()
                ));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        // scan all shadow score keys
        var keys = redisTemplate.keys("shadow:scores:*");
        if (keys == null || keys.isEmpty()) {
            return ResponseEntity.ok(Map.of("total", 0));
        }

        int total = 0, disagreements = 0;
        for (String key : keys) {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            total++;
            String pd = (String) entries.get("primary_decision");
            String sd = (String) entries.get("shadow_decision");
            if (pd != null && sd != null && !pd.equals(sd)) {
                disagreements++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total_scored", total);
        result.put("disagreements", disagreements);
        result.put("agreement_rate",
                total > 0 ? String.format("%.1f%%", (1.0 - (double) disagreements / total) * 100) : "N/A");

        return ResponseEntity.ok(result);
    }
}