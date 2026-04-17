# Stage 1 — build the fat JAR from the project root (multi-module Maven build)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy parent POM and stub pom.xml files for every module so Maven reactor resolves
COPY pom.xml ./
COPY common/pom.xml common/
COPY common/src common/src/
COPY config-server/pom.xml config-server/
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY task-service/pom.xml task-service/
COPY audit-service/pom.xml audit-service/
COPY file-service/pom.xml file-service/
COPY search-service/pom.xml search-service/
COPY notification-service/pom.xml notification-service/
COPY reporting-service/pom.xml reporting-service/

# Download dependencies for config-server only (offline-friendly layer)
RUN mvn dependency:go-offline -pl config-server -am -q

# Copy config-server sources and build
COPY config-server/src config-server/src/
RUN mvn package -pl config-server -am -DskipTests -q

# Stage 2 — minimal runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/config-server/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
