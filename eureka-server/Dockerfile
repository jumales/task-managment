# Stage 1 — build the fat JAR from the project root (multi-module Maven build)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy parent POM and common module first for layer caching
COPY pom.xml ./
COPY common/pom.xml common/
COPY common/src common/src/
COPY eureka-server/pom.xml eureka-server/

# Download dependencies for eureka-server only (offline-friendly layer)
RUN mvn dependency:go-offline -pl eureka-server -am -q

# Copy eureka-server sources and build
COPY eureka-server/src eureka-server/src/
RUN mvn package -pl eureka-server -am -DskipTests -q

# Stage 2 — minimal runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/eureka-server/target/*.jar app.jar
EXPOSE 8761 8762
ENTRYPOINT ["java", "-jar", "app.jar"]
