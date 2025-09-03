package com.example.order.dto;

import java.math.BigDecimal;

public class OrderResponse {
    private boolean success;
    private String message;
    private Long orderId;
    private String status;
    private BigDecimal totalAmount;
    private String paymentTransactionId;
    
    // Constructors
    public OrderResponse() {}
    
    public OrderResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public OrderResponse(boolean success, String message, Long orderId, String status, BigDecimal totalAmount, String paymentTransactionId) {
        this.success = success;
        this.message = message;
        this.orderId = orderId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.paymentTransactionId = paymentTransactionId;
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    
    public String getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(String paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }
}
