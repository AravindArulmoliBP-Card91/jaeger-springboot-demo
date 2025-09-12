package com.example.order.service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.order.entity.Order;

@Service
public class AsyncNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncNotificationService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Async method that sends email notification
     * OpenTelemetry automatically traces this across thread boundaries
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> sendEmailNotification(Order order) {
        try {
            // Simulate email sending delay
            Thread.sleep(500);
            
            // Cache notification status - automatically traced by OpenTelemetry
            String notificationKey = "notification:email:" + order.getId();
            redisTemplate.opsForValue().set(notificationKey, "SENT", Duration.ofDays(7));
            
            logger.info("📧 Email notification sent for order: " + order.getId());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email notification failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Async method that sends SMS notification
     * Demonstrates parallel async operations with tracing
     */
    @Async("taskExecutor")
    public CompletableFuture<String> sendSmsNotification(Order order) {
        try {
            // Simulate SMS sending delay
            Thread.sleep(300);
            
            // Cache SMS status - automatically traced
            String smsKey = "notification:sms:" + order.getId();
            redisTemplate.opsForValue().set(smsKey, "DELIVERED", Duration.ofDays(7));
            
            String message = "SMS sent for order " + order.getId();
            logger.info("📱 " + message);
            
            return CompletableFuture.completedFuture(message);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SMS notification failed", e);
        }
    }
    
    /**
     * Async audit logging
     * Shows how database operations are traced in async methods
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> logOrderEvent(Long orderId, String event, String details) {
        try {
            // Simulate audit processing
            Thread.sleep(100);
            
            // Cache audit log - traced automatically
            String auditKey = "audit:order:" + orderId + ":" + System.currentTimeMillis();
            String auditData = String.format("{\"event\":\"%s\",\"details\":\"%s\",\"timestamp\":%d}", 
                event, details, System.currentTimeMillis());
            
            redisTemplate.opsForValue().set(auditKey, auditData, Duration.ofDays(30));
            
            logger.info("📝 Audit logged: " + event + " for order " + orderId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Audit logging failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
}
