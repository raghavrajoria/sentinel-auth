package com.sentinel.audit.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "decisions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Decision {

    @Id
    @Column(name = "tx_id")
    private String txId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "decision")
    private String decision;

    @Column(name = "risk_score")
    private int riskScore;

    @Column(name = "ml_risk_score")
    private int mlRiskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_fired", columnDefinition = "jsonb")
    private String rulesFired;

    @Column(name = "latency_ms")
    private long latencyMs;

    @Column(name = "ml_fallback")
    private boolean mlFallback;

    @Column(name = "created_at")
    private Instant createdAt;
}