# ---------- Build & Run Stage (single stage) ----------
FROM gradle:9.0.0-jdk21-alpine

WORKDIR /app

# 1. Copy only build scripts first to cache dependencies
COPY build.gradle settings.gradle ./

# Pre-download dependencies (caches between builds unless build.gradle changes)
RUN gradle dependencies --no-daemon || return 0

# 2. Copy source code
COPY src ./src

# Build the fat JAR (skip tests for dev speed)
RUN gradle clean bootJar -x test --no-daemon

# Expose dev port
EXPOSE 8080

# Run the app directly
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "build/libs/app.jar"]
