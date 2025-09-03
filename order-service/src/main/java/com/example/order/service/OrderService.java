package com.example.order.service;

import com.example.order.dto.*;
import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Value("${payment.service.url}")
    private String paymentServiceUrl;
    
    @Transactional
    public OrderResponse processOrder(OrderRequest request) {
        try {
            // Calculate total amount (simplified - using base price of $100 per product)
            BigDecimal unitPrice = new BigDecimal("100.00");
            BigDecimal totalAmount = unitPrice.multiply(new BigDecimal(request.getQuantity()));
            
            // Create order record
            Order order = new Order(
                request.getCustomerName(), 
                request.getProductId(), 
                request.getQuantity(), 
                totalAmount
            );
            order.setStatus("CREATED");
            Order savedOrder = orderRepository.save(order);
            
            // Cache order information in Redis
            String orderCacheKey = "order:" + savedOrder.getId();
            redisTemplate.opsForValue().set(orderCacheKey, savedOrder, Duration.ofHours(24));
            
            // Store customer order count in Redis
            String customerKey = "customer:orders:" + request.getCustomerName();
            redisTemplate.opsForValue().increment(customerKey, 1);
            redisTemplate.expire(customerKey, Duration.ofDays(30));
            
            // Process payment
            PaymentRequest paymentRequest = new PaymentRequest(
                savedOrder.getId(),
                request.getProductId(),
                request.getQuantity(),
                totalAmount,
                request.getPaymentMethod()
            );
            
            String paymentUrl = paymentServiceUrl + "/pay";
            ResponseEntity<PaymentResponse> paymentResponse = restTemplate.postForEntity(
                paymentUrl, 
                paymentRequest, 
                PaymentResponse.class
            );
            
            if (paymentResponse.getBody() != null && paymentResponse.getBody().isSuccess()) {
                // Payment successful - update order status
                savedOrder.setStatus("COMPLETED");
                orderRepository.save(savedOrder);
                
                // Update cache
                redisTemplate.opsForValue().set(orderCacheKey, savedOrder, Duration.ofHours(24));
                
                return new OrderResponse(
                    true,
                    "Order processed successfully",
                    savedOrder.getId(),
                    savedOrder.getStatus(),
                    savedOrder.getTotalAmount(),
                    paymentResponse.getBody().getTransactionId()
                );
            } else {
                // Payment failed - update order status
                savedOrder.setStatus("PAYMENT_FAILED");
                orderRepository.save(savedOrder);
                
                // Update cache
                redisTemplate.opsForValue().set(orderCacheKey, savedOrder, Duration.ofHours(24));
                
                String errorMessage = paymentResponse.getBody() != null ? 
                    paymentResponse.getBody().getMessage() : "Payment processing failed";
                return new OrderResponse(false, "Order failed: " + errorMessage);
            }
            
        } catch (Exception e) {
            return new OrderResponse(false, "Order processing error: " + e.getMessage());
        }
    }
    
    public Order getOrderById(Long orderId) {
        // Check cache first
        String cacheKey = "order:" + orderId;
        Order cachedOrder = (Order) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedOrder != null) {
            return cachedOrder;
        }
        
        // Fetch from database
        return orderRepository.findById(orderId).orElse(null);
    }
}
