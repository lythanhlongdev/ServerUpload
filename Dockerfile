# ======================
# STAGE 1: BUILD
# ======================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Cache dependency
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests


# ======================
# STAGE 2: RUNTIME
# ======================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Tạo thư mục runtime
RUN mkdir -p /data/uploads /logs

# Copy jar
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Chạy app
ENTRYPOINT ["java","-Xms256m","-Xmx512m","-jar","app.jar"]