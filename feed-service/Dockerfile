# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-amazoncorretto-17-alpine AS builder
WORKDIR /build

COPY pom.xml                pom.xml
COPY common/pom.xml         common/pom.xml
COPY eureka-server/pom.xml  eureka-server/pom.xml
COPY api-gateway/pom.xml    api-gateway/pom.xml
COPY user-service/pom.xml   user-service/pom.xml
COPY post-service/pom.xml   post-service/pom.xml
COPY feed-service/pom.xml   feed-service/pom.xml

RUN mvn dependency:go-offline -pl feed-service -am --no-transfer-progress

COPY common/src      common/src
COPY feed-service/src feed-service/src
RUN mvn package -pl feed-service -am -DskipTests --no-transfer-progress

# ── Stage 2: Run ─────────────────────────────────────────────────────────────
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /build/feed-service/target/feed-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
