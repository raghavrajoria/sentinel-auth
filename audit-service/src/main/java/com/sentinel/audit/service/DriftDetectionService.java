package com.sentinel.audit.service;

import com.sentinel.audit.model.Decision;
import com.sentinel.audit.repository.DecisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class DriftDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DriftDetectionService.class);

    // Baseline distribution — expected rates from Phase 1 load test
    private static final double BASELINE_BLOCK_RATE  = 0.30;
    private static final double BASELINE_REVIEW_RATE = 0.40;
    private static final double BASELINE_ALLOW_RATE  = 0.30;

    // Alert thresholds — flag if deviation exceeds this
    private static final double DRIFT_THRESHOLD = 0.15;

    // Minimum decisions needed for meaningful analysis
    private static final int MIN_SAMPLE_SIZE = 10;

    private final DecisionRepository decisionRepository;

    // Latest report — accessible via controller
    private final AtomicReference<Map<String, Object>> latestReport =
            new AtomicReference<>(Map.of("status", "No report yet - waiting for first run"));

    public DriftDetectionService(DecisionRepository decisionRepository) {
        this.decisionRepository = decisionRepository;
    }

    // Run every hour
    @Scheduled(fixedRateString = "PT1H")
    public void detectDrift() {
        try {
            Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
            List<Decision> recent = decisionRepository.findRecentDecisions(since);

            if (recent.size() < MIN_SAMPLE_SIZE) {
                log.info("Drift check skipped — only {} decisions in last hour (min {})",
                        recent.size(), MIN_SAMPLE_SIZE);
                return;
            }

            // compute current distribution
            Map<String, Long> counts = recent.stream()
                    .collect(Collectors.groupingBy(
                            d -> normalizeDecision(d.getDecision()),
                            Collectors.counting()
                    ));

            int total = recent.size();
            double blockRate  = counts.getOrDefault("BLOCK",  0L) / (double) total;
            double reviewRate = counts.getOrDefault("REVIEW", 0L) / (double) total;
            double allowRate  = counts.getOrDefault("ALLOW",  0L) / (double) total;

            // compute deviations from baseline
            double blockDrift  = Math.abs(blockRate  - BASELINE_BLOCK_RATE);
            double reviewDrift = Math.abs(reviewRate - BASELINE_REVIEW_RATE);
            double allowDrift  = Math.abs(allowRate  - BASELINE_ALLOW_RATE);

            boolean driftDetected = blockDrift  > DRIFT_THRESHOLD
                    || reviewDrift > DRIFT_THRESHOLD
                    || allowDrift  > DRIFT_THRESHOLD;

            // build report
            Map<String, Object> report = new HashMap<>();
            report.put("evaluated_at",      Instant.now().toString());
            report.put("sample_size",       total);
            report.put("window_hours", 24);
            report.put("drift_detected",     driftDetected);

            report.put("current_block_rate",  pct(blockRate));
            report.put("current_review_rate", pct(reviewRate));
            report.put("current_allow_rate",  pct(allowRate));

            report.put("baseline_block_rate",  pct(BASELINE_BLOCK_RATE));
            report.put("baseline_review_rate", pct(BASELINE_REVIEW_RATE));
            report.put("baseline_allow_rate",  pct(BASELINE_ALLOW_RATE));

            report.put("block_drift",  pct(blockDrift));
            report.put("review_drift", pct(reviewDrift));
            report.put("allow_drift",  pct(allowDrift));
            report.put("threshold",    pct(DRIFT_THRESHOLD));

            report.put("recommendation", driftDetected
                    ? "ALERT: Model behavior has shifted. Review recent rule changes or retrain model."
                    : "OK: Decision distribution within expected range.");

            latestReport.set(report);

            if (driftDetected) {
                log.warn("MODEL DRIFT DETECTED — block={} review={} allow={} (sample={})",
                        pct(blockRate), pct(reviewRate), pct(allowRate), total);
            } else {
                log.info("Drift check OK — block={} review={} allow={} (sample={})",
                        pct(blockRate), pct(reviewRate), pct(allowRate), total);
            }

        } catch (Exception e) {
            log.error("Drift detection failed: {}", e.getMessage(), e);
        }
    }

    // normalize APPROVED/REJECTED back to ALLOW/BLOCK for distribution
    private String normalizeDecision(String decision) {
        return switch (decision) {
            case "APPROVED"    -> "ALLOW";
            case "REJECTED"    -> "BLOCK";
            case "PENDING_OTP" -> "REVIEW";
            default            -> decision;
        };
    }

    private String pct(double val) {
        return String.format("%.1f%%", val * 100);
    }

    public Map<String, Object> getLatestReport() {
        return latestReport.get();
    }

    // Trigger manually for testing
    public void runNow() {
        detectDrift();
    }
}