# =========================================================
# Multi-stage Dockerfile for Oracle Data Studio & DBMS Workbench
# Lightweight, production-grade container for Cloud & AWS deployment
# =========================================================

# Stage 1: Build Application with Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Production JRE Runtime (Minimal footprint < 150MB)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-privileged system user
RUN addgroup -S dbmsgroup && adduser -S dbmsuser -G dbmsgroup

# Copy compiled JAR from build stage
COPY --from=build /app/target/oracle-dbms-dashboard-*.jar app.jar
RUN chown -R dbmsuser:dbmsgroup /app

USER dbmsuser

# Default port expected by Render and cloud hosting
ENV PORT=8080
EXPOSE 8080

# Run JVM with memory optimizations for cloud free tiers (512MB RAM containers)
ENTRYPOINT ["java", "-Xmx384m", "-XX:+UseG1GC", "-Dserver.port=${PORT}", "-jar", "app.jar"]
