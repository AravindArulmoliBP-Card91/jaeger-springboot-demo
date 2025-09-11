package com.example.order.service;

import com.example.order.dto.*;
import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.common.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private AsyncNotificationService asyncNotificationService;
    
    @Value("${payment.service.url}")
    private String paymentServiceUrl;
    
    @Transactional
    public OrderResponse processOrder(OrderRequest request) {
        // Get current span to add events (logs will appear in Jaeger "Logs" tab)
        Span currentSpan = Span.current();
        
        logger.info("Starting order processing for customer: {}, productId: {}, quantity: {}", 
                   request.getCustomerName(), request.getProductId(), request.getQuantity());
        
        // Add span event for Jaeger "Logs" tab
        currentSpan.addEvent("Starting order processing", 
            Attributes.builder()
                .put("customer.name", request.getCustomerName())
                .put("product.id", request.getProductId())
                .put("quantity", request.getQuantity())
                .build());
        
        try {
            // Calculate total amount (simplified - using base price of $100 per product)
            BigDecimal unitPrice = new BigDecimal("100.00");
            BigDecimal totalAmount = unitPrice.multiply(new BigDecimal(request.getQuantity()));
            logger.debug("Calculated total amount: {} for quantity: {}", totalAmount, request.getQuantity());
            
            // Create order record
            Order order = new Order(
                request.getCustomerName(), 
                request.getProductId(), 
                request.getQuantity(), 
                totalAmount
            );
            order.setStatus("CREATED");
            Order savedOrder = orderRepository.save(order);
            logger.info("Order created successfully with ID: {} and status: {}", savedOrder.getId(), savedOrder.getStatus());
            
            // Add span event
            currentSpan.addEvent("Order created successfully",
                Attributes.builder()
                    .put("order.id", savedOrder.getId())
                    .put("order.status", savedOrder.getStatus())
                    .put("total.amount", totalAmount.toString())
                    .build());
            
            // Cache order information in Redis
            String orderCacheKey = "order:" + savedOrder.getId();
            redisTemplate.opsForValue().set(orderCacheKey, savedOrder, Duration.ofHours(24));
            logger.debug("Order cached in Redis with key: {}", orderCacheKey);
            
            // Store customer order count in Redis
            String customerKey = "customer:orders:" + request.getCustomerName();
            redisTemplate.opsForValue().increment(customerKey, 1);
            redisTemplate.expire(customerKey, Duration.ofDays(30));
            logger.debug("Updated customer order count for: {}", request.getCustomerName());
            
            // Process payment
            PaymentRequest paymentRequest = new PaymentRequest(
                savedOrder.getId(),
                request.getProductId(),
                request.getQuantity(),
                totalAmount,
                request.getPaymentMethod()
            );
            
            String paymentUrl = paymentServiceUrl + "/pay";
            logger.info("Initiating payment processing for order: {} with amount: {} via {}", 
                       savedOrder.getId(), totalAmount, request.getPaymentMethod());
            
            // Add span event
            currentSpan.addEvent("Initiating payment processing",
                Attributes.builder()
                    .put("order.id", savedOrder.getId())
                    .put("payment.amount", totalAmount.toString())
                    .put("payment.method", request.getPaymentMethod())
                    .build());
            
            ResponseEntity<PaymentResponse> paymentResponse = restTemplate.postForEntity(
                paymentUrl, 
                paymentRequest, 
                PaymentResponse.class
            );
            
            if (paymentResponse.getBody() != null && paymentResponse.getBody().isSuccess()) {
                logger.info("Payment successful for order: {} with transaction ID: {}", 
                           savedOrder.getId(), paymentResponse.getBody().getTransactionId());
                
                // Add span event
                currentSpan.addEvent("Payment successful",
                    Attributes.builder()
                        .put("order.id", savedOrder.getId())
                        .put("transaction.id", paymentResponse.getBody().getTransactionId())
                        .put("payment.status", "SUCCESS")
                        .build());
                
                // Payment successful - update order status
                savedOrder.setStatus("COMPLETED");
                orderRepository.save(savedOrder);
                logger.debug("Order status updated to COMPLETED for order: {}", savedOrder.getId());
                
                // Update cache
                redisTemplate.opsForValue().set(orderCacheKey, savedOrder, Duration.ofHours(24));
                
                // Trigger async operations - these will be traced automatically by OpenTelemetry
                logger.info("Triggering async post-processing for order: {}", savedOrder.getId());
                
                // Add span event
                currentSpan.addEvent("Triggering async post-processing",
                    Attributes.builder()
                        .put("order.id", savedOrder.getId())
                        .put("async.operations", "email,sms,audit")
                        .build());
                
                triggerAsyncPostProcessing(savedOrder);
                
                return new OrderResponse(
                    true,
                    "Order processed successfully",
                    savedOrder.getId(),
                    savedOrder.getStatus(),
                    savedOrder.getTotalAmount(),
                    paymentResponse.getBody().getTransactionId()
                );
            } else {
                logger.warn("Payment failed for order: {}", savedOrder.getId());
                
                // Add span event
                String errorMessage = paymentResponse.getBody() != null ? 
                    paymentResponse.getBody().getMessage() : "Payment processing failed";
                currentSpan.addEvent("Payment failed",
                    Attributes.builder()
                        .put("order.id", savedOrder.getId())
                        .put("payment.status", "FAILED")
                        .put("error.message", errorMessage)
                        .build());
                
                // Payment failed - update order status
                savedOrder.setStatus("PAYMENT_FAILED");
                orderRepository.save(savedOrder);
                logger.debug("Order status updated to PAYMENT_FAILED for order: {}", savedOrder.getId());
                
                // Update cache
                redisTemplate.opsForValue().set(orderCacheKey, savedOrder, Duration.ofHours(24));
                
                logger.error("Payment failure details: {}", errorMessage);
                return new OrderResponse(false, "Order failed: " + errorMessage);
            }
            
        } catch (Exception e) {
            logger.error("Order processing failed with exception: {}", e.getMessage(), e);
            
            // Add span event for exception
            currentSpan.addEvent("Order processing failed",
                Attributes.builder()
                    .put("error.type", e.getClass().getSimpleName())
                    .put("error.message", e.getMessage())
                    .build());
            
            return new OrderResponse(false, "Order processing error: " + e.getMessage());
        }
    }
    
    public Order getOrderById(Long orderId) {
        logger.debug("Retrieving order by ID: {}", orderId);
        
        // Check cache first
        String cacheKey = "order:" + orderId;
        Order cachedOrder = (Order) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedOrder != null) {
            logger.debug("Order found in cache: {}", orderId);
            return cachedOrder;
        }
        
        // Fetch from database
        logger.debug("Order not in cache, fetching from database: {}", orderId);
        return orderRepository.findById(orderId).orElse(null);
    }
    
    /**
     * Trigger async post-processing operations
     * OpenTelemetry will automatically trace these async operations and maintain trace context
     */
    private void triggerAsyncPostProcessing(Order order) {
        // Fire parallel async operations - all will be traced with proper parent-child relationships
        CompletableFuture<Void> emailFuture = asyncNotificationService.sendEmailNotification(order);
        CompletableFuture<String> smsFuture = asyncNotificationService.sendSmsNotification(order);
        CompletableFuture<Void> auditFuture = asyncNotificationService.logOrderEvent(
            order.getId(), "ORDER_COMPLETED", "Order successfully processed and payment confirmed"
        );
        
        // Combine async operations (optional - for demonstration)
        CompletableFuture.allOf(emailFuture, smsFuture, auditFuture)
            .thenRun(() -> {
                System.out.println("✅ All async post-processing completed for order: " + order.getId());
            })
            .exceptionally(throwable -> {
                System.err.println("❌ Some async operations failed for order: " + order.getId() + 
                                 " - " + throwable.getMessage());
                return null;
            });
    }
}
