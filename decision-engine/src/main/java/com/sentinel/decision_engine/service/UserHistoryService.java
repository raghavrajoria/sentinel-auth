package com.sentinel.decision_engine.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class UserHistoryService {

    private final RedisTemplate<String, String> redisTemplate;

    public UserHistoryService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<String> getRecentTransactions(String userId) {
        String key = "user:" + userId + ":history";
        List<String> history = redisTemplate.opsForList().range(key, 0, 49);
        return history != null ? history : List.of();
    }

    public void addTransaction(String userId, String txJson) {
        String key = "user:" + userId + ":history";
        redisTemplate.opsForList().leftPush(key, txJson);
        redisTemplate.opsForList().trim(key, 0, 49);
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    // idempotency check
    public String getIdempotentResponse(String txId) {
        return redisTemplate.opsForValue().get("idem:" + txId);
    }

    public void saveIdempotentResponse(String txId, String responseJson) {
        redisTemplate.opsForValue().set("idem:" + txId, responseJson, Duration.ofHours(24));
    }
}