FROM ubuntu:latest
LABEL authors="orsted"

# ===== STAGE 1: BUILD =====
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom trước để tận dụng cache
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests


# ===== STAGE 2: RUNTIME =====
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Tạo thư mục upload + log
RUN mkdir -p /data/uploads
RUN mkdir -p /logs

# Copy jar từ stage build
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar","top", "-b"]