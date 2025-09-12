# OpenTelemetry + Jaeger Implementation Guide

This guide demonstrates how to add distributed tracing with OpenTelemetry and Jaeger to existing Spring Boot microservices, based on our working implementation.

## 📋 Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Infrastructure Setup](#infrastructure-setup)
4. [Application Configuration](#application-configuration)
5. [Docker Configuration](#docker-configuration)
6. [Testing and Verification](#testing-and-verification)
7. [Troubleshooting](#troubleshooting)

## Overview

**What you'll achieve:**
- Automatic instrumentation of HTTP requests, database calls, and Redis operations
- Cross-service trace propagation
- Centralized trace collection and visualization in Jaeger UI
- Zero code changes required for basic tracing

**Architecture:**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Service A     │───▶│   Service B     │───▶│   Service C     │
│ (OpenTel Agent) │    │ (OpenTel Agent) │    │ (OpenTel Agent) │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Jaeger Collector                            │
│                 (OTLP HTTP Endpoint)                           │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
                    ┌─────────────────────┐
                    │     Jaeger UI       │
                    │  (Trace Viewer)     │
                    └─────────────────────┘
```

## Prerequisites

- Docker and Docker Compose
- Existing Spring Boot microservices
- Java 8+ (Java 21 recommended)

## Infrastructure Setup

### 1. Add Jaeger to Docker Compose

Add the Jaeger service to your `docker-compose.yml`:

```yaml
services:
  jaeger:
    image: jaegertracing/all-in-one:1.50
    container_name: jaeger
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    ports:
      - "16686:16686"  # Jaeger UI
      - "4317:4317"    # OTLP gRPC receiver
      - "4318:4318"    # OTLP HTTP receiver
    networks:
      - your-network-name
```

### 2. Download OpenTelemetry Java Agent

```bash
# Download the latest OpenTelemetry Java Agent
curl -L -o opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
```

## Application Configuration

### 1. Gradle Dependencies

Add Jackson JSR310 support to handle LocalDateTime serialization (if using Redis caching):

```gradle
dependencies {
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
}
```

### 2. Application Properties

Add Jackson configuration to your `application.yml`:

```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
```

### 3. Redis Configuration (if applicable)

Update your Redis configuration to handle LocalDateTime serialization:

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Configure ObjectMapper to handle LocalDateTime
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values with configured ObjectMapper
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        return template;
    }
}
```

## Docker Configuration

### 1. Update Dockerfile

Modify your service Dockerfiles to include the OpenTelemetry agent in the entrypoint:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app
COPY build.gradle .
COPY settings.gradle .
COPY gradle/ ./gradle/
COPY src ./src

RUN apk add --no-cache gradle
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/build/libs/your-service-1.0.0.jar app.jar

EXPOSE 8080

# Include OpenTelemetry agent in entrypoint
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

### 2. Update Docker Compose Services

Configure each microservice with OpenTelemetry environment variables:

```yaml
services:
  your-service:
    build: ./your-service
    container_name: your-service
    environment:
      # Your existing environment variables
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/your_db
      - SPRING_REDIS_HOST=redis
      
      # OpenTelemetry Configuration
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318
      - OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
      - OTEL_SERVICE_NAME=your-service
      - OTEL_TRACES_SAMPLER=always_on
      - OTEL_RESOURCE_ATTRIBUTES=service.name=your-service,service.version=1.0.0
    ports:
      - "8080:8080"
    volumes:
      # Mount the OpenTelemetry agent
      - ./opentelemetry-javaagent.jar:/app/opentelemetry-javaagent.jar
    depends_on:
      jaeger:
        condition: service_started
      # Your other dependencies
    networks:
      - your-network-name
```

### 3. Complete Example Configuration

Here's how we configured our three microservices:

```yaml
services:
  # Infrastructure
  jaeger:
    image: jaegertracing/all-in-one:1.50
    container_name: jaeger
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    ports:
      - "16686:16686"
      - "4317:4317"
      - "4318:4318"
    networks:
      - microservices-network

  # Microservices
  inventory-service:
    build: ./inventory-service
    container_name: inventory-service
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/microservices_db
      - SPRING_REDIS_HOST=redis
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318
      - OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
      - OTEL_SERVICE_NAME=inventory-service
      - OTEL_TRACES_SAMPLER=always_on
      - OTEL_RESOURCE_ATTRIBUTES=service.name=inventory-service,service.version=1.0.0
    ports:
      - "8082:8080"
    volumes:
      - ./opentelemetry-javaagent.jar:/app/opentelemetry-javaagent.jar
    networks:
      - microservices-network

  payment-service:
    build: ./payment-service
    container_name: payment-service
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/microservices_db
      - SPRING_REDIS_HOST=redis
      - INVENTORY_SERVICE_URL=http://inventory-service:8080
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318
      - OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
      - OTEL_SERVICE_NAME=payment-service
      - OTEL_TRACES_SAMPLER=always_on
      - OTEL_RESOURCE_ATTRIBUTES=service.name=payment-service,service.version=1.0.0
    ports:
      - "8081:8080"
    volumes:
      - ./opentelemetry-javaagent.jar:/app/opentelemetry-javaagent.jar
    networks:
      - microservices-network

  order-service:
    build: ./order-service
    container_name: order-service
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/microservices_db
      - SPRING_REDIS_HOST=redis
      - PAYMENT_SERVICE_URL=http://payment-service:8080
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318
      - OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
      - OTEL_SERVICE_NAME=order-service
      - OTEL_TRACES_SAMPLER=always_on
      - OTEL_RESOURCE_ATTRIBUTES=service.name=order-service,service.version=1.0.0
    ports:
      - "8080:8080"
    volumes:
      - ./opentelemetry-javaagent.jar:/app/opentelemetry-javaagent.jar
    networks:
      - microservices-network
```

## Environment Variables Reference

| Variable | Description | Example |
|----------|-------------|---------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Jaeger collector endpoint | `http://jaeger:4318` |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | Protocol for OTLP export | `http/protobuf` |
| `OTEL_SERVICE_NAME` | Service identifier in traces | `order-service` |
| `OTEL_TRACES_SAMPLER` | Sampling strategy | `always_on` (dev), `parentbased_traceidratio` (prod) |
| `OTEL_RESOURCE_ATTRIBUTES` | Additional service metadata | `service.name=order-service,service.version=1.0.0` |
| `OTEL_TRACES_SAMPLER_ARG` | Sampling ratio (if using ratio sampler) | `0.1` (10% sampling) |

## Testing and Verification

### 1. Start Services

```bash
docker-compose up --build -d
```

### 2. Check Service Startup

Look for OpenTelemetry agent initialization in logs:

```bash
docker-compose logs your-service | grep -i otel
```

Expected output:
```
[otel.javaagent 2025-09-03 10:21:27:224 +0000] [main] INFO io.opentelemetry.javaagent.tooling.VersionLogger - opentelemetry-javaagent - version: 2.19.0
```

### 3. Generate Traces

Make HTTP requests to trigger cross-service calls:

```bash
# Example API call
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Test User",
    "productId": 1,
    "quantity": 2,
    "paymentMethod": "CREDIT_CARD"
  }'
```

### 4. View Traces in Jaeger

1. Open Jaeger UI: http://localhost:16686
2. Select your service from the dropdown
3. Click "Find Traces"
4. Click on a trace to see the detailed view

### 5. What You Should See

**Service List**: All your microservices should appear in the service dropdown

**Trace Details**: Each trace should show:
- HTTP requests between services
- Database queries (JPA/Hibernate operations)
- Redis operations
- Custom spans (if added)
- Error information (if any)

**Trace Flow Example**:
```
order-service
├── POST /order
├── MySQL: INSERT INTO orders
├── Redis: SET order:1
├── HTTP: POST payment-service/pay
│   ├── MySQL: INSERT INTO payments
│   └── HTTP: POST inventory-service/reserve
│       ├── MySQL: SELECT FROM inventory
│       ├── MySQL: UPDATE inventory
│       └── Redis: SET inventory:reserved:1
└── MySQL: UPDATE orders SET status='COMPLETED'
```

## Troubleshooting

### Services Not Appearing in Jaeger

**Issue**: Only `jaeger-all-in-one` appears in service dropdown

**Solutions**:
1. Check OpenTelemetry agent is loading:
   ```bash
   docker-compose logs service-name | grep -i otel
   ```

2. Verify environment variables are set correctly:
   ```bash
   docker exec service-name env | grep OTEL
   ```

3. Check Jaeger connectivity:
   ```bash
   docker exec service-name curl -I http://jaeger:4318/v1/traces
   ```

### Jackson Serialization Errors

**Issue**: `LocalDateTime` serialization errors with Redis

**Solution**: Add Jackson JSR310 dependency and configure ObjectMapper (see Application Configuration section)

### Traces Not Propagating

**Issue**: Each service creates separate traces instead of one continuous trace

**Solutions**:
1. Ensure all services use the same OpenTelemetry agent version
2. Verify HTTP headers are being propagated (automatic with RestTemplate)
3. Check service-to-service URLs are correct

### Performance Impact

**Issue**: Concerned about tracing overhead

**Solutions**:
1. Use sampling in production:
   ```yaml
   - OTEL_TRACES_SAMPLER=parentbased_traceidratio
   - OTEL_TRACES_SAMPLER_ARG=0.1  # 10% sampling
   ```

2. Monitor resource usage and adjust sampling rate accordingly

### Container Startup Issues

**Issue**: Services fail to start after adding OpenTelemetry

**Solutions**:
1. Verify agent JAR file is mounted correctly:
   ```bash
   docker exec service-name ls -la /app/opentelemetry-javaagent.jar
   ```

2. Check Dockerfile entrypoint syntax:
   ```dockerfile
   ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
   ```

## Production Considerations

### 1. Sampling Configuration

```yaml
# Production sampling - trace 10% of requests
- OTEL_TRACES_SAMPLER=parentbased_traceidratio
- OTEL_TRACES_SAMPLER_ARG=0.1
```

### 2. Resource Limits

```yaml
services:
  your-service:
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
```

### 3. Jaeger Storage

For production, consider using persistent storage:

```yaml
jaeger:
  image: jaegertracing/all-in-one:1.50
  environment:
    - SPAN_STORAGE_TYPE=elasticsearch
    - ES_SERVER_URLS=http://elasticsearch:9200
```

### 4. Security

- Use HTTPS endpoints in production
- Configure authentication for Jaeger UI
- Limit trace data retention period

## Summary

This implementation provides:
- ✅ **Zero-code instrumentation** for HTTP, database, and cache operations
- ✅ **Automatic trace propagation** across microservices
- ✅ **Centralized trace collection** in Jaeger
- ✅ **Visual trace analysis** with detailed timing information
- ✅ **Production-ready configuration** options

The OpenTelemetry Java Agent automatically instruments popular frameworks including:
- Spring Boot / Spring MVC
- JPA / Hibernate
- JDBC drivers
- Redis clients
- HTTP clients (RestTemplate, WebClient)
- And many more...

No code changes are required for basic tracing - just configuration and deployment setup!
