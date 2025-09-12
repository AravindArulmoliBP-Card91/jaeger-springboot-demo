# OpenTelemetry Log-Trace Integration Guide

This guide demonstrates how to achieve **zero-code log-trace correlation** in Spring Boot microservices using OpenTelemetry. The implementation automatically converts log messages into span events and injects trace context into logs without requiring any changes to existing business logic.

## 🎯 What This Implementation Achieves

✅ **Automatic Log-to-Span Events** - Every log message becomes a span event automatically  
✅ **Trace Context in Logs** - Trace ID and Span ID appear in log output  
✅ **Zero Code Changes** - No modifications to existing service code required  
✅ **Minimal Configuration** - Only 2 files and 1 dependency needed  
✅ **Production Ready** - Works with existing logging patterns  

## 🚀 The Magic: How It Works

### 1. **Automatic Span Event Creation**
Every log statement in your code automatically becomes a span event in Jaeger:

```java
// Your existing code (unchanged):
logger.info("Processing order for customer: {}", customerName);
logger.error("Payment failed for order: {}", orderId);

// What happens automatically:
// → Span event: "Processing order for customer: John Doe"
// → Span event: "Payment failed for order: 123"
// → Both appear in Jaeger trace timeline
```

### 2. **Trace Context Injection**
Log messages automatically include trace context:

```
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Processing order for customer: John Doe
```

Where:
- `1a2b3c4d5e6f7g8h` = Trace ID
- `9i0j1k2l3m4n5o6p` = Span ID

## 📁 Implementation: Only 3 Things Required

### 1. **Add One Dependency** (`build.gradle`)

```gradle
dependencies {
    // OpenTelemetry Logback MDC for log-trace correlation
    implementation 'io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:1.32.0-alpha'
    
    // OpenTelemetry API for span events
    implementation 'io.opentelemetry:opentelemetry-api'
}
```

### 2. **Create Logback Configuration** (`logback-spring.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Include Spring Boot's default logback configuration -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
    
    <!-- OpenTelemetry MDC Appender for trace context in logs -->
    <appender name="OTEL_MDC" class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender">
        <appender-ref ref="CONSOLE"/>
    </appender>
    
    <!-- Custom Auto Span Event Appender - converts logs to span events automatically -->
    <appender name="AUTO_SPAN_EVENT" class="com.example.order.config.AutoSpanEventAppender">
        <!-- This appender automatically adds log messages as span events -->
    </appender>
    
    <!-- Logger configurations -->
    <logger name="com.example.order" level="INFO" additivity="false">
        <appender-ref ref="OTEL_MDC"/>
        <appender-ref ref="AUTO_SPAN_EVENT"/>
    </logger>
    
    <!-- Root logger -->
    <root level="INFO">
        <appender-ref ref="OTEL_MDC"/>
        <appender-ref ref="AUTO_SPAN_EVENT"/>
    </root>
</configuration>
```

### 3. **Create Auto Span Event Appender** (`AutoSpanEventAppender.java`)

```java
package com.example.order.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;

/**
 * Custom Logback appender that automatically converts log messages to OpenTelemetry span events.
 * This eliminates the need to manually call currentSpan.addEvent() alongside every logger statement.
 */
public class AutoSpanEventAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        try {
            // Get the current active span
            Span currentSpan = Span.current();
            
            // Only add events to actual spans (not the default no-op span)
            if (currentSpan.getSpanContext().isValid()) {
                
                // Create attributes from log event
                Attributes attributes = Attributes.builder()
                    .put(AttributeKey.stringKey("log.level"), event.getLevel().toString())
                    .put(AttributeKey.stringKey("log.logger"), event.getLoggerName())
                    .put(AttributeKey.stringKey("log.thread"), event.getThreadName())
                    .build();
                
                // Add exception info if present
                if (event.getThrowableProxy() != null) {
                    attributes = Attributes.builder()
                        .putAll(attributes)
                        .put(AttributeKey.stringKey("exception.type"), event.getThrowableProxy().getClassName())
                        .put(AttributeKey.stringKey("exception.message"), event.getThrowableProxy().getMessage())
                        .build();
                    
                    // Mark span as error if it's an ERROR level log with exception
                    if ("ERROR".equals(event.getLevel().toString())) {
                        currentSpan.setStatus(StatusCode.ERROR, event.getFormattedMessage());
                    }
                }
                
                // Add the log message as a span event
                currentSpan.addEvent(event.getFormattedMessage(), attributes);
            }
            
        } catch (Exception e) {
            // Don't let logging failures break the application
            System.err.println("AutoSpanEventAppender error: " + e.getMessage());
        }
    }
}
```

### 4. **Optional: Update Application Configuration** (`application.yml`)

```yaml
logging:
  level:
    com.example.order: INFO
    io.opentelemetry: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{trace_id:-},%X{span_id:-}] %logger{36} - %msg%n"
  logback:
    config: classpath:logback-spring.xml
```

## 🔍 How It Works Under the Hood

### 1. **OpenTelemetry MDC Appender**
- **Purpose**: Injects trace context into MDC (Mapped Diagnostic Context)
- **Result**: `%X{trace_id}` and `%X{span_id}` are available in log patterns
- **Automatic**: Works with any logger statement

### 2. **Auto Span Event Appender**
- **Purpose**: Converts every log message into a span event
- **Process**:
  1. Intercepts every log event
  2. Gets the current active span
  3. Creates span event with log message and attributes
  4. Adds to the current trace

### 3. **Zero Code Integration**
- **No changes needed** to existing `logger.info()`, `logger.error()` statements
- **Automatic context propagation** across service boundaries
- **Works with all log levels** (DEBUG, INFO, WARN, ERROR)

## 🎯 What You See in Jaeger

### **Before Implementation**
```
order-service
├── HTTP POST /order                    [120ms]
├── OrderService.processOrder()         [115ms]
├── MySQL: INSERT INTO orders          [5ms]
└── HTTP POST payment-service/pay      [95ms]
```

### **After Implementation**
```
order-service
├── HTTP POST /order                    [120ms]
├── OrderService.processOrder()         [115ms]
│   ├── 📝 "Starting order processing for customer: John Doe"
│   ├── 📝 "Order created successfully with ID: 1"
│   ├── 📝 "Initiating payment processing for order: 1"
│   └── 📝 "Payment successful for order: 1"
├── MySQL: INSERT INTO orders          [5ms]
└── HTTP POST payment-service/pay      [95ms]
    └── 📝 "Processing payment for order: 1"
```

## 🚀 Testing the Implementation

### 1. **Start Services**
```bash
docker-compose up --build -d
```

### 2. **Generate Test Traces**
```bash
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Test User",
    "productId": 1,
    "quantity": 2,
    "paymentMethod": "CREDIT_CARD"
  }'
```

### 3. **View in Jaeger**
1. Open Jaeger UI: http://localhost:16686
2. Select service: `order-service`
3. Click "Find Traces"
4. Click on a trace
5. **See log messages as span events in the timeline!**

### 4. **Check Console Logs**
```
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Starting order processing for customer: Test User, productId: 1, quantity: 2
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Order created successfully with ID: 1 and status: CREATED
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO [1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] c.e.o.s.OrderService - Initiating payment processing for order: 1 with amount: 200.00 via CREDIT_CARD
```

## 🎯 Key Benefits

### 1. **Zero Code Changes**
- ✅ No modifications to existing service code
- ✅ No manual `span.addEvent()` calls needed
- ✅ Works with existing logging patterns

### 2. **Enhanced Debugging**
- ✅ See log messages directly in trace timeline
- ✅ Correlate logs with specific spans
- ✅ Track request flow through logs

### 3. **Production Ready**
- ✅ Minimal performance overhead
- ✅ Graceful error handling
- ✅ Works with all log levels

### 4. **Easy Maintenance**
- ✅ Configuration-only approach
- ✅ Easy to enable/disable
- ✅ No business logic changes

## 🔧 Advanced Configuration

### **Custom Log Patterns**
```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{trace_id:-},%X{span_id:-}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{trace_id:-},%X{span_id:-}] %logger{36} - %msg%n"
```

### **Selective Logging**
```xml
<!-- Only add span events for specific loggers -->
<logger name="com.example.order.service" level="INFO" additivity="false">
    <appender-ref ref="OTEL_MDC"/>
    <appender-ref ref="AUTO_SPAN_EVENT"/>
</logger>

<!-- Exclude noisy loggers from span events -->
<logger name="org.springframework.web" level="INFO" additivity="false">
    <appender-ref ref="OTEL_MDC"/>
    <!-- No AUTO_SPAN_EVENT for web logs -->
</logger>
```

### **Exception Handling**
The `AutoSpanEventAppender` automatically:
- ✅ Captures exception details in span events
- ✅ Marks spans as ERROR when exceptions occur
- ✅ Includes exception type and message

## 🚨 Troubleshooting

### **Issue: No Span Events in Jaeger**
**Check:**
1. Verify `AutoSpanEventAppender` is configured in logback
2. Ensure OpenTelemetry agent is running
3. Check that spans are active when logging occurs

### **Issue: No Trace Context in Logs**
**Check:**
1. Verify `OTEL_MDC` appender is configured
2. Ensure `%X{trace_id}` pattern is in log format
3. Check that OpenTelemetry agent is loaded

### **Issue: Performance Impact**
**Solutions:**
1. Use selective logging (exclude noisy loggers)
2. Adjust log levels to reduce volume
3. Monitor span event count in Jaeger

## 📊 Production Considerations

### **1. Log Volume Management**
```xml
<!-- Reduce span events for high-volume loggers -->
<logger name="org.springframework.web" level="WARN" additivity="false">
    <appender-ref ref="OTEL_MDC"/>
    <!-- No AUTO_SPAN_EVENT -->
</logger>
```

### **2. Error-Only Span Events**
```xml
<!-- Only add span events for errors -->
<logger name="com.example.order" level="ERROR" additivity="false">
    <appender-ref ref="OTEL_MDC"/>
    <appender-ref ref="AUTO_SPAN_EVENT"/>
</logger>
```

### **3. Sampling Configuration**
```yaml
# Reduce trace volume in production
environment:
  - OTEL_TRACES_SAMPLER=parentbased_traceidratio
  - OTEL_TRACES_SAMPLER_ARG=0.1  # 10% sampling
```

## 🎉 Summary

This implementation provides **complete log-trace integration** with:

✅ **Zero code changes** - No modifications to existing service code  
✅ **Automatic span events** - Every log becomes a span event  
✅ **Trace context in logs** - Trace ID and Span ID in log output  
✅ **Minimal configuration** - Only 2 files and 1 dependency  
✅ **Production ready** - Works with existing logging patterns  

**The result**: Enhanced observability and debugging capabilities with virtually no effort!

---

**Next Steps**: Apply the same configuration to all your microservices for complete log-trace correlation across your entire system.