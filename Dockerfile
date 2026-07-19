# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Install dos2unix and bash to fix Windows line-ending and execution bugs
RUN apk add --no-cache dos2unix bash

# Copy Maven wrapper and pom.xml first
COPY mvnw pom.xml ./
COPY .mvn .mvn/

# FIX: Convert Windows line endings to Linux and give execute permissions
RUN dos2unix mvnw && chmod +x mvnw

RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src src/
RUN ./mvnw package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# FIX: Use Railway's dynamic PORT variable instead of hardcoded 8080
ENV PORT=8080
EXPOSE ${PORT}

# Update healthcheck to respect the dynamic port
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=50", \
    "-XX:NativeMemoryTracking=summary", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
