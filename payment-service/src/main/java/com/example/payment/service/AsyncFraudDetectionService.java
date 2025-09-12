package com.example.payment.service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.payment.dto.PaymentRequest;

@Service
public class AsyncFraudDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncFraudDetectionService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Async fraud detection check
     * Demonstrates async processing with return values
     */
    @Async("taskExecutor")
    public CompletableFuture<Boolean> performFraudCheck(PaymentRequest request) {
        try {
            // Simulate fraud detection processing time
            Thread.sleep(800);
            
            // Check customer payment history - Redis operations traced automatically
            String customerKey = "fraud:history:" + request.getOrderId();
            String paymentHistory = (String) redisTemplate.opsForValue().get(customerKey);
            
            // Simple fraud detection logic
            boolean isFraudulent = request.getAmount().doubleValue() > 10000.0; // Flag large amounts
            
            // Cache fraud check result
            String resultKey = "fraud:check:" + request.getOrderId();
            String result = isFraudulent ? "FLAGGED" : "APPROVED";
            redisTemplate.opsForValue().set(resultKey, result, Duration.ofHours(24));
            
            // Update customer history
            redisTemplate.opsForValue().increment(customerKey + ":count", 1);
            redisTemplate.expire(customerKey + ":count", Duration.ofDays(30));
            
            logger.info("🔍 Fraud check completed for order " + request.getOrderId() + ": " + result);
            
            return CompletableFuture.completedFuture(!isFraudulent);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Fraud detection failed", e);
        }
    }
    
    /**
     * Async payment risk scoring
     * Shows complex async processing with multiple Redis operations
     */
    @Async("taskExecutor")
    public CompletableFuture<Integer> calculateRiskScore(PaymentRequest request) {
        try {
            // Simulate risk calculation processing
            Thread.sleep(400);
            
            int riskScore = 0;
            
            // Check payment amount risk - Redis operations traced
            if (request.getAmount().doubleValue() > 5000) {
                riskScore += 30;
            }
            
            // Check payment method risk
            String methodKey = "risk:method:" + request.getPaymentMethod();
            String methodRisk = (String) redisTemplate.opsForValue().get(methodKey);
            if (methodRisk == null) {
                // Set default risk scores for payment methods
                int methodRiskScore = "CREDIT_CARD".equals(request.getPaymentMethod()) ? 10 : 20;
                redisTemplate.opsForValue().set(methodKey, String.valueOf(methodRiskScore), Duration.ofDays(1));
                riskScore += methodRiskScore;
            } else {
                riskScore += Integer.parseInt(methodRisk);
            }
            
            // Cache risk score
            String scoreKey = "risk:score:" + request.getOrderId();
            redisTemplate.opsForValue().set(scoreKey, String.valueOf(riskScore), Duration.ofHours(24));
            
            logger.info("⚡ Risk score calculated for order " + request.getOrderId() + ": " + riskScore);
            
            return CompletableFuture.completedFuture(riskScore);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Risk scoring failed", e);
        }
    }
}
