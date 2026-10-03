# syntax=docker/dockerfile:1

# Stage 1: compile and package. The BuildKit cache mount keeps the Maven
# repository between builds, so only changed sources cost anything.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

# Stage 2: run the jar on a JRE. curl is here for the compose healthcheck.
FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 trackit

WORKDIR /app

# Exactly one jar is produced; *.jar.original is left behind by the Spring Boot
# repackage step and is not matched here.
COPY --from=build /build/target/*.jar app.jar

USER trackit

EXPOSE 8080

# Let the JVM size its heap from the container limit rather than the host's RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
