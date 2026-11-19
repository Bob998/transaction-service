# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests clean package && \
    cp target/transaction-service-*.jar /workspace/app.jar

FROM eclipse-temurin:17-jdk-jammy AS runtime

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="transaction-service" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="transaction-service"

RUN apt-get update && \
    apt-get install --no-install-recommends -y curl procps && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app --home-dir /app app

WORKDIR /app
COPY --from=builder --chown=app:app /workspace/app.jar app.jar

USER 10001:10001
EXPOSE 8080

ENV SERVER_PORT=8080 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:NativeMemoryTracking=summary -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD curl --fail --silent "http://localhost:${SERVER_PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
