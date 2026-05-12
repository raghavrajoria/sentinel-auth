package com.sentinel.decision_engine.grpc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import sentinel.Scoring;
import sentinel.ScoringServiceGrpc;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SentryMlClient {

    private ManagedChannel channel;
    private ScoringServiceGrpc.ScoringServiceBlockingStub stub;
    private CircuitBreaker circuitBreaker;
    private static final Logger log = LoggerFactory.getLogger(SentryMlClient.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public SentryMlClient(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder
                .forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        stub = ScoringServiceGrpc.newBlockingStub(channel);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("sentryMl");

        System.out.println("=== CB CONFIG ===");
        System.out.println("Window size: " + circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize());
        System.out.println("Min calls: " + circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        System.out.println("Failure threshold: " + circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
        System.out.println("=================");

        circuitBreaker.getEventPublisher()
                .onStateTransition(e -> System.out.println(
                        "Circuit breaker state: "
                                + e.getStateTransition()));

        System.out.println("SentryMlClient ready. CB state: "
                + circuitBreaker.getState());
    }

    public int score(String txId, String userId,
                     double amount, int historyCount) {
        log.warn(">>> score() called, CB state: {}", circuitBreaker.getState());
        return circuitBreaker.executeSupplier(() ->
                callMl(txId, userId, amount, historyCount));
    }

    private int callMl(String txId, String userId,
                       double amount, int historyCount) {
        log.warn(">>> callMl() executing for tx={}", txId);
        Scoring.ScoreRequest request = Scoring.ScoreRequest.newBuilder()
                .setTxId(txId)
                .setUserId(userId)
                .setAmount(amount)
                .setHistoryCount(historyCount)
                .build();

        Scoring.ScoreResponse response = stub
                .withDeadlineAfter(500, TimeUnit.MILLISECONDS)
                .score(request);

        log.warn(">>> gRPC success tx={} score={}", txId, response.getRiskScore());
        return response.getRiskScore();
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}