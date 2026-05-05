# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src/ src/
RUN mvn clean package -DskipTests -Dquarkus.package.type=uber-jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/*-runner.jar application.jar

EXPOSE 8080
CMD ["java", "-jar", "application.jar"]

