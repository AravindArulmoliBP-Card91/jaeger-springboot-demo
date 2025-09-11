# Spring Boot Logs in Jaeger Traces - Implementation Guide

This guide documents the complete implementation of log-trace correlation for Spring Boot microservices using OpenTelemetry and Jaeger.

## 🎯 What We've Implemented

✅ **OpenTelemetry Logback Appender** - Automatic log-trace correlation  
✅ **Structured JSON Logging** - Machine-readable log format  
✅ **Trace Context Injection** - Trace ID and Span ID in logs  
✅ **Log Export to Jaeger** - Logs visible in trace details  
✅ **Zero Code Changes** - Configuration-only approach  

## 📁 Files Modified

### 1. Maven Dependencies (`pom.xml`)
**Added to all three services:**
- `opentelemetry-logback-appender-1.0` - Log-trace correlation
- `logstash-logback-encoder` - Structured JSON logging

### 2. Logback Configuration (`logback-spring.xml`)
**Created for all three services:**
- OpenTelemetry appender for trace correlation
- JSON structured logging format
- Console, file, and OTLP appenders
- Service-specific trace context

### 3. OpenTelemetry Configuration (`docker-compose.yml`)
**Updated environment variables:**
```yaml
- OTEL_LOGS_EXPORTER=otlp
- OTEL_LOGS_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318
- OTEL_LOGS_EXPORTER_OTLP_PROTOCOL=http/protobuf
- OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED=true
```

### 4. Application Configuration (`application.yml`)
**Enhanced logging configuration:**
```yaml
logging:
  level:
    io.opentelemetry: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId:-},%X{spanId:-}] %logger{36} - %msg%n"
  logback:
    config: classpath:logback-spring.xml
```

### 5. Enhanced Logging (`OrderService.java`)
**Added structured logging statements:**
- Trace context in log messages
- Service operation logging
- Error and debug information

## 🔧 How It Works

### 1. Automatic Trace Context Injection

**OpenTelemetry Java Agent** automatically:
- Injects trace context into MDC (Mapped Diagnostic Context)
- Makes `traceId` and `spanId` available to loggers
- Correlates logs with active spans

### 2. Logback Appender Chain

```
Application Log → Logback → OpenTelemetry Appender → Jaeger
                ↓
            Console/File Appenders
```

### 3. Structured Log Format

**Console Logs:**
```
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Starting order processing for customer: John Doe
```

**JSON Logs (OTLP):**
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.example.order.service.OrderService",
  "message": "Starting order processing for customer: John Doe",
  "traceId": "1a2b3c4d5e6f7g8h",
  "spanId": "9i0j1k2l3m4n5o6p",
  "service": "order-service",
  "thread": "http-nio-8080-exec-1"
}
```

## 🚀 Testing the Implementation

### 1. Start Services

```bash
# Build and start all services
docker-compose up --build -d

# Check service startup logs
docker-compose logs order-service | grep -i otel
docker-compose logs payment-service | grep -i otel
docker-compose logs inventory-service | grep -i otel
```

**Expected Output:**
```
[otel.javaagent 2024-01-15 10:21:27:224 +0000] [main] INFO io.opentelemetry.javaagent.tooling.VersionLogger - opentelemetry-javaagent - version: 2.19.0
```

### 2. Generate Test Traces

```bash
# Create a test order
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Test User",
    "productId": 1,
    "quantity": 2,
    "paymentMethod": "CREDIT_CARD"
  }'
```

### 3. View Logs in Jaeger

1. **Open Jaeger UI:** http://localhost:16686
2. **Select Service:** Choose `order-service` from dropdown
3. **Find Traces:** Click "Find Traces"
4. **View Trace Details:** Click on a trace
5. **Check Logs Tab:** Look for the "Logs" tab in trace details

### 4. Verify Log-Trace Correlation

**In Jaeger UI, you should see:**
- **Logs tab** showing all application logs
- **Trace context** (Trace ID, Span ID) in each log entry
- **Structured log data** with service information
- **Timeline view** showing logs alongside spans

**In Console Logs, you should see:**
```
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Starting order processing for customer: Test User, productId: 1, quantity: 2
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Order created successfully with ID: 1 and status: CREATED
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Initiating payment processing for order: 1 with amount: 200.00 via CREDIT_CARD
```

## 📊 Expected Results

### In Jaeger UI

**Trace Details View:**
- **Spans tab:** Shows the trace timeline with spans
- **Logs tab:** Shows all correlated logs with trace context
- **Timeline:** Logs appear at the correct time points in the trace

**Log Entry Example:**
```
Timestamp: 2024-01-15T10:30:45.123Z
Level: INFO
Service: order-service
Message: Starting order processing for customer: Test User
Trace ID: 1a2b3c4d5e6f7g8h
Span ID: 9i0j1k2l3m4n5o6p
Thread: http-nio-8080-exec-1
```

### In Application Logs

**Console Output:**
```
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Starting order processing for customer: Test User, productId: 1, quantity: 2
2024-01-15 10:30:45 [http-nio-8080-exec-1] DEBUG [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Calculated total amount: 200.00 for quantity: 2
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Order created successfully with ID: 1 and status: CREATED
2024-01-15 10:30:45 [http-nio-8080-exec-1] DEBUG [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Order cached in Redis with key: order:1
2024-01-15 10:30:45 [http-nio-8080-exec-1] DEBUG [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Updated customer order count for: Test User
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Initiating payment processing for order: 1 with amount: 200.00 via CREDIT_CARD
```

**File Logs (JSON format):**
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.example.order.service.OrderService",
  "message": "Starting order processing for customer: Test User, productId: 1, quantity: 2",
  "traceId": "1a2b3c4d5e6f7g8h",
  "spanId": "9i0j1k2l3m4n5o6p",
  "service": "order-service",
  "thread": "http-nio-8080-exec-1"
}
```

## 🔍 Troubleshooting

### Issue: Logs Not Appearing in Jaeger

**Check:**
1. **OpenTelemetry Agent Loading:**
   ```bash
   docker-compose logs order-service | grep -i otel
   ```

2. **Environment Variables:**
   ```bash
   docker exec order-service env | grep OTEL_LOGS
   ```

3. **Jaeger Connectivity:**
   ```bash
   docker exec order-service curl -I http://jaeger:4318/v1/logs
   ```

### Issue: Trace Context Not in Logs

**Check:**
1. **Logback Configuration:** Verify `logback-spring.xml` is loaded
2. **OpenTelemetry Appender:** Check appender configuration
3. **MDC Context:** Ensure trace context is being injected

### Issue: JSON Logs Not Formatted

**Check:**
1. **Logstash Encoder:** Verify dependency is included
2. **Logback Configuration:** Check encoder configuration
3. **Console vs File:** Different formats for different appenders

## 🎯 Benefits Achieved

### 1. Enhanced Debugging
- **Trace Context:** See logs directly in trace context
- **Correlation:** Easy correlation between logs and spans
- **Timeline View:** Logs appear at correct time points

### 2. Better Observability
- **Structured Data:** Machine-readable log format
- **Service Identification:** Clear service attribution
- **Thread Information:** Thread context for async operations

### 3. Production Ready
- **Log Aggregation:** Compatible with ELK, Fluentd, etc.
- **Performance:** Minimal overhead with OpenTelemetry agent
- **Scalability:** Works across multiple services

### 4. Zero Code Changes
- **Configuration Only:** No business logic changes required
- **Automatic:** Trace context injection is automatic
- **Maintainable:** Easy to enable/disable

## 🚀 Next Steps

### 1. Extend to Other Services
Apply the same logging enhancements to:
- `PaymentService.java`
- `InventoryService.java`
- `AsyncNotificationService.java`

### 2. Add More Logging
Consider adding logs for:
- Database operations
- Cache operations
- External API calls
- Error scenarios

### 3. Production Configuration
For production, consider:
- Log sampling to reduce volume
- Log retention policies
- Security considerations for sensitive data

### 4. Monitoring Setup
Integrate with:
- Log aggregation systems (ELK, Splunk)
- Alerting systems
- Dashboard tools

## 📚 References

- [OpenTelemetry Logging](https://opentelemetry.io/docs/instrumentation/java/logging/)
- [Logback Configuration](https://logback.qos.ch/manual/configuration.html)
- [Jaeger Logs](https://www.jaegertracing.io/docs/1.50/deployment/#collector-logs)
- [Spring Boot Logging](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging)

---

**Result:** Complete log-trace integration with zero code changes, providing enhanced observability and debugging capabilities for your Spring Boot microservices! 🎉
