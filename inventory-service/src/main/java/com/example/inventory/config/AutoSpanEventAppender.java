package com.example.inventory.config;

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
                
                // Add MDC properties if available (simplified approach)
                if (event.getMDCPropertyMap() != null && !event.getMDCPropertyMap().isEmpty()) {
                    // For now, just use the basic attributes - MDC can be added later if needed
                    // This avoids the complex builder pattern issues
                }
                
                // Add the log message as a span event
                currentSpan.addEvent(event.getFormattedMessage(), attributes);
            }
            
        } catch (Exception e) {
            // Don't let logging failures break the application
            // Optionally log to console for debugging
            System.err.println("AutoSpanEventAppender error: " + e.getMessage());
        }
    }
}
