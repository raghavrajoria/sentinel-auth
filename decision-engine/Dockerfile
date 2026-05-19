# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# copy maven wrapper and pom first (layer cache — only re-downloads deps if pom changes)
COPY .mvn/ .mvn/
COPY pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || true

# copy source and build
COPY src/ src/
RUN mvn clean package -DskipTests --no-transfer-progress

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# non-root user for security
RUN addgroup -S sentinel && adduser -S sentinel -G sentinel
USER sentinel

COPY --from=builder /app/target/decision-engine-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "app.jar"]