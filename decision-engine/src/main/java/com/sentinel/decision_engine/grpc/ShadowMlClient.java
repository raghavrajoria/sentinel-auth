package com.sentinel.decision_engine.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sentinel.Scoring;
import sentinel.ScoringServiceGrpc;

import java.util.concurrent.TimeUnit;

@Component
public class ShadowMlClient {

    private static final Logger log = LoggerFactory.getLogger(ShadowMlClient.class);
    private ManagedChannel channel;
    private ScoringServiceGrpc.ScoringServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder
                .forAddress("localhost", 50052)
                .usePlaintext()
                .build();
        stub = ScoringServiceGrpc.newBlockingStub(channel);
        log.info("ShadowMlClient ready on port 50052");
    }

    public int score(String txId, String userId, double amount, int historyCount) {
        try {
            Scoring.ScoreRequest request = Scoring.ScoreRequest.newBuilder()
                    .setTxId(txId)
                    .setUserId(userId)
                    .setAmount(amount)
                    .setHistoryCount(historyCount)
                    .build();

            Scoring.ScoreResponse response = stub
                    .withDeadlineAfter(100, TimeUnit.MILLISECONDS)
                    .score(request);

            return response.getRiskScore();
        } catch (Exception e) {
            log.warn("Shadow ML failed for tx={}: {}", txId, e.getMessage());
            return -1; // -1 means shadow unavailable — don't affect primary
        }
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}