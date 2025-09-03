package com.example.payment.service;

import com.example.payment.dto.*;
import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class PaymentService {
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        try {
            // First, try to reserve inventory
            ReservationRequest reservationRequest = new ReservationRequest(
                request.getProductId(), 
                request.getQuantity()
            );
            
            String reservationUrl = inventoryServiceUrl + "/reserve";
            ResponseEntity<ReservationResponse> reservationResponse = restTemplate.postForEntity(
                reservationUrl, 
                reservationRequest, 
                ReservationResponse.class
            );
            
            if (reservationResponse.getBody() == null || !reservationResponse.getBody().isSuccess()) {
                return new PaymentResponse(false, "Failed to reserve inventory: " + 
                    (reservationResponse.getBody() != null ? reservationResponse.getBody().getMessage() : "Unknown error"));
            }
            
            // Create payment record
            Payment payment = new Payment(request.getOrderId(), request.getAmount(), request.getPaymentMethod());
            payment.setTransactionId(UUID.randomUUID().toString());
            
            // Simulate payment processing
            boolean paymentSuccess = simulatePaymentProcessing(payment);
            
            if (paymentSuccess) {
                payment.setStatus("COMPLETED");
                Payment savedPayment = paymentRepository.save(payment);
                
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
                return new PaymentResponse(false, "Payment processing failed");
            }
            
        } catch (Exception e) {
            return new PaymentResponse(false, "Payment processing error: " + e.getMessage());
        }
    }
    
    private boolean simulatePaymentProcessing(Payment payment) {
        // Simulate payment gateway call
        // In real world, this would call external payment gateway
        try {
            Thread.sleep(100); // Simulate network delay
            
            // 95% success rate for demo purposes
            return Math.random() > 0.05;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
