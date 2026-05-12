# Sentinel-Auth LLM Context Dump

## Project Overview
**PROJECT:** Sentinel-Auth — Real-Time Fraud Detection Engine
**One-liner:** Adaptive fraud detection engine scoring transactions using behavioral baselines, XGBoost ML inference (AUC 0.903), velocity checks, and circuit breakers — processing 530+ RPS at p99 185ms.
**Location:** D:\projects\sentinel-auth

## Tech Stack
* Java 21 (Temurin at C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot)
* Spring Boot 3.5.14, Maven
* Python 3.10.11, XGBoost, scikit-learn, gRPC
* Redis 7, Postgres 16, Kafka (Confluent 7.6.0)
* Prometheus, Grafana
* Resilience4j 2.2.0
* k6 (at D:\projects\sentinel-auth\infra\k6\k6.exe)
* Docker Desktop

## Environment Details
Critical env — run every new PowerShell session:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot" 
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH 
```

Maven must use local settings:
```powershell
mvn spring-boot:run -s .mvn/settings.xml 
```

* All files must be UTF-8 without BOM.
* AuditService needs VM option: `-Duser.timezone=UTC`

## Infrastructure (Docker)
* sentinel-redis → localhost:6379 
* sentinel-postgres → localhost:5432 (user: sentinel, pass: sentinel123, db: sentinel) 
* sentinel-kafka → localhost:9092 
* sentinel-prometheus → localhost:9090 
* sentinel-grafana → localhost:3000 (user: admin, pass: admin) 

Start all: `cd D:\projects\sentinel-auth && docker-compose up -d`

## Folder Structure
```
sentinel-auth/ 
├── docker-compose.yml 
├── decision-engine/ ← Java, Spring Boot (COMPLETE) 
│   ├── .mvn/ 
│   ├── pom.xml 
│   └── src/main/java/com/sentinel/decision_engine/ 
│       ├── DecisionEngineApplication.java 
│       ├── config/ 
│       │   ├── RedisConfig.java 
│       │   └── KafkaConfig.java 
│       ├── controller/ 
│       │   └── CheckController.java ← Micrometer metrics + velocity 
│       ├── grpc/ 
│       │   └── SentryMlClient.java ← 500ms deadline, Resilience4j CB 
│       ├── kafka/ 
│       │   ├── DecisionProducer.java 
│       │   └── DecisionConsumer.java 
│       ├── model/ 
│       │   ├── Rule.java 
│       │   └── DecisionEvent.java 
│       └── service/ 
│           ├── RuleEngineService.java 
│           ├── UserHistoryService.java 
│           └── VelocityService.java ← sliding window velocity checks 
├── sentry-ml/ ← Python gRPC ML service (COMPLETE) 
│   ├── venv/ 
│   ├── models/ 
│   │   ├── xgboost_sentinel.json ← trained model, AUC 0.903 
│   │   └── features.json ← 29 feature names in order 
│   ├── proto/scoring.proto 
│   └── app/ 
│       ├── server.py ← real XGBoost inference, warmup on startup 
│       ├── scoring_pb2.py 
│       └── scoring_pb2_grpc.py 
├── audit-service/ ← Java, Spring Boot (COMPLETE) 
│   └── src/main/java/com/sentinel/audit/ 
│       ├── consumer/DecisionConsumer.java 
│       ├── controller/ExplanationController.java 
│       ├── model/Decision.java 
│       └── repository/DecisionRepository.java 
├── notifier/ ← Java, Spring Boot (COMPLETE) 
│   └── src/main/java/com/sentinel/notifier/ 
│       ├── consumer/AlertConsumer.java 
│       ├── ws/AlertWebSocketHandler.java 
│       └── ws/WebSocketConfig.java 
│   └── src/main/resources/static/ 
│       └── dashboard.html ← live BLOCK alert dashboard 
└── infra/ 
    ├── postgres/init.sql 
    ├── prometheus/prometheus.yml 
    ├── grafana/ 
    ├── k6/ 
    │   ├── k6.exe 
    │   └── load-test.js 
    └── kafka/ 
```

## What's Built
* Day 1 — Spring Boot skeleton, POST /v1/check
* Day 2 — Redis integration: user history (last 50 txns, 7d TTL) + idempotency (SETNX, 24h TTL)
* Day 3 — JSON Rule Engine: rules loaded from rules.json
* Day 4 — Python gRPC ML service: now running real XGBoost model
* Day 5 — Kafka pipeline: transactions.decisions + transactions.raw topics, async publish
* Day 6 — Audit service: Kafka → Postgres, GET /v1/explanation/{txId}
* Day 7 — Prometheus + Grafana: decisions_blocked_total, decisions_allowed_total, decisions_latency_seconds, decisions_ml_fallback_total
* Day 8 — k6 load test: 530 RPS, p99 185ms, 0% errors, 95,489 requests
* Day 9 — Circuit breaker (Resilience4j) + WebSocket notifier + live dashboard at http://localhost:8082/dashboard.html
* Day 10 — Real XGBoost model: trained on 590k IEEE-CIS transactions, AUC 0.903, 29 features, exported to xgboost_sentinel.json, served via gRPC with warmup

## Circuit Breaker Config
```yaml
resilience4j: 
  circuitbreaker: 
    instances: 
      sentryMl: 
        sliding-window-size: 5 
        minimum-number-of-calls: 5 
        failure-rate-threshold: 50 
        wait-duration-in-open-state: 60s 
        permitted-number-of-calls-in-half-open-state: 2 
        automatic-transition-from-open-to-half-open-enabled: true 
```

## ML Model Details
* Dataset: IEEE-CIS Fraud Detection (Kaggle)
* Training samples: 472,432
* Validation samples: 118,108
* Validation AUC: 0.9030
* Fraud rate: 3.5%
* Top features: C8, C5, C4, addr2, C1, C2, id_04, C6, C9, C7, TransactionAmt
* Class imbalance handled with scale_pos_weight
* Exported as XGBoost native format, loaded via XGBClassifier.load_model()

## Transaction Flow
POST /check → Redis idempotency check (SETNX idem:{tx_id}) → Redis user history fetch → Velocity check (sliding window, Redis) → gRPC call to Python ML (500ms deadline, Resilience4j CB) → Real XGBoost inference (29 features, AUC 0.903) → JSON rule engine evaluation → Combine ML score + rule risk + velocity risk → final decision → Increment Micrometer counters → HTTP response to client → Async Kafka publish to transactions.decisions → audit-service consumes → writes to Postgres → notifier consumes → WebSocket broadcast on BLOCK 

## Bugs Fixed
* BOM encoding on every PowerShell-created file — broke Postgres init, proto compilation, Python
* bakrep2 TWMS Maven repo leaking into project — fixed with .mvn/settings.xml
* Java 21 not on PATH — JAVA_HOME pointing to Java 8
* Asia/Calcutta timezone rejected by Postgres JDBC — fixed with -Duser.timezone=UTC
* gRPC cold connect latency 1700ms on first call — fixed with warmup call in server.py
* rules_fired JSONB type mismatch — fixed with @JdbcTypeCode(SqlTypes.JSON)
* Resilience4j config under spring.resilience4j instead of top-level — caused -1.0% failure rate
* gRPC proto field names camelCase in Java, snake_case in Python — txId → tx_id
* ScoreResponse(riskScore=...) → ScoreResponse(risk_score=...) in Python
* XGBoost first inference slow — warmup call at server startup

## What's Next
1. README + architecture diagram
2. Loom demo recording
3. Push to GitHub
4. Shadow mode
5. Behavioral baseline per user
6. Step-up auth via email OTP
7. Dynamic rules via Redis Pub/Sub
8. React review dashboard
9. MLflow model versioning
10. GitHub Actions CI/CD

## Resume Bullets
* Architected microservices fraud detection system processing 530+ RPS at p99 185ms using Java 21 Virtual Threads and async Kafka publishing
* Trained XGBoost on 590k IEEE-CIS transactions achieving AUC 0.903, served via Python gRPC service with 500ms SLA and Resilience4j circuit breaker preventing cascade failures
* Implemented idempotent decision API with Redis SETNX, velocity checks with sliding window counters, and JSON rule engine — eliminating duplicate charges and detecting transaction bursts
* Designed Kafka-based audit pipeline with /explanation API returning full decision trace including ML features, model version, and rules fired — enabling regulatory compliance
