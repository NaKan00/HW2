# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app --chmod=0444 /build/target/marketplace-1.0.0.jar app.jar
COPY --chown=app:app --chmod=0444 scripts/HealthProbe.java /app/HealthProbe.java
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
