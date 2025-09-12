package com.example.payment.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.ReservationRequest;
import com.example.payment.dto.ReservationResponse;
import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private AsyncFraudDetectionService asyncFraudDetectionService;
    
    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        logger.info("Starting payment processing for order: {}, productId: {}, amount: {}, paymentMethod: {}", 
                   request.getOrderId(), request.getProductId(), request.getAmount(), request.getPaymentMethod());
        
        try {
            // First, try to reserve inventory
            ReservationRequest reservationRequest = new ReservationRequest(
                request.getProductId(), 
                request.getQuantity()
            );
            
            String reservationUrl = inventoryServiceUrl + "/reserve";
            logger.info("Initiating inventory reservation for order: {} with productId: {} and quantity: {}", 
                       request.getOrderId(), request.getProductId(), request.getQuantity());
            
            ResponseEntity<ReservationResponse> reservationResponse = restTemplate.postForEntity(
                reservationUrl, 
                reservationRequest, 
                ReservationResponse.class
            );
            
            if (reservationResponse.getBody() == null || !reservationResponse.getBody().isSuccess()) {
                logger.warn("Inventory reservation failed for order: {} - {}", 
                           request.getOrderId(), 
                           reservationResponse.getBody() != null ? reservationResponse.getBody().getMessage() : "Unknown error");
                return new PaymentResponse(false, "Failed to reserve inventory: " + 
                    (reservationResponse.getBody() != null ? reservationResponse.getBody().getMessage() : "Unknown error"));
            }
            
            logger.info("Inventory reservation successful for order: {}", request.getOrderId());
            
            // Create payment record
            Payment payment = new Payment(request.getOrderId(), request.getAmount(), request.getPaymentMethod());
            payment.setTransactionId(UUID.randomUUID().toString());
            logger.debug("Created payment record with transaction ID: {} for order: {}", 
                        payment.getTransactionId(), request.getOrderId());
            
            // Simulate payment processing
            logger.info("Processing payment for order: {} with transaction ID: {} and amount: {}", 
                       request.getOrderId(), payment.getTransactionId(), request.getAmount());
            boolean paymentSuccess = simulatePaymentProcessing(payment);
            
            if (paymentSuccess) {
                payment.setStatus("COMPLETED");
                Payment savedPayment = paymentRepository.save(payment);
                logger.info("Payment completed successfully for order: {} with transaction ID: {} and payment ID: {}", 
                           request.getOrderId(), savedPayment.getTransactionId(), savedPayment.getId());
                
                // Trigger async fraud detection and risk scoring - traced automatically by OpenTelemetry
                logger.info("Triggering async fraud analysis for order: {}", request.getOrderId());
                triggerAsyncFraudAnalysis(request);
                
                return new PaymentResponse(
                    true,
                    "Payment processed successfully",
                    savedPayment.getId(),
                    savedPayment.getTransactionId(),
                    savedPayment.getAmount(),
                    savedPayment.getStatus()
                );
            } else {
                payment.setStatus("FAILED");
                paymentRepository.save(payment);
                logger.warn("Payment processing failed for order: {} with transaction ID: {}", 
                           request.getOrderId(), payment.getTransactionId());
                return new PaymentResponse(false, "Payment processing failed");
            }
            
        } catch (Exception e) {
            logger.error("Payment processing failed with exception for order: {} - {}", 
                        request.getOrderId(), e.getMessage(), e);
            return new PaymentResponse(false, "Payment processing error: " + e.getMessage());
        }
    }
    
    private boolean simulatePaymentProcessing(Payment payment) {
        // Simulate payment gateway call
        // In real world, this would call external payment gateway
        logger.debug("Simulating payment gateway call for transaction: {}", payment.getTransactionId());
        try {
            Thread.sleep(100); // Simulate network delay
            
            // 95% success rate for demo purposes
            boolean success = Math.random() > 0.05;
            logger.debug("Payment gateway simulation result for transaction {}: {}", 
                        payment.getTransactionId(), success ? "SUCCESS" : "FAILED");
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Payment processing interrupted for transaction: {}", payment.getTransactionId());
            return false;
        }
    }
    
    /**
     * Trigger async fraud analysis operations
     * OpenTelemetry will automatically trace these async operations
     */
    private void triggerAsyncFraudAnalysis(PaymentRequest request) {
        // Run parallel async fraud detection - both operations will be traced
        CompletableFuture<Boolean> fraudCheckFuture = asyncFraudDetectionService.performFraudCheck(request);
        CompletableFuture<Integer> riskScoreFuture = asyncFraudDetectionService.calculateRiskScore(request);
        
        // Combine results for logging (optional - demonstrates CompletableFuture composition)
        fraudCheckFuture.thenCombine(riskScoreFuture, (isClean, riskScore) -> {
            logger.info("🔍 Fraud analysis completed for order " + request.getOrderId() + 
                             " - Clean: " + isClean + ", Risk Score: " + riskScore);
            return null;
        }).exceptionally(throwable -> {
            logger.error("❌ Fraud analysis failed for order: " + request.getOrderId() + 
                             " - " + throwable.getMessage());
            return null;
        });
    }
}
