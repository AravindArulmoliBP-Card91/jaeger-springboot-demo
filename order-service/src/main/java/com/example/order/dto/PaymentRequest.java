package com.example.order.dto;

import java.math.BigDecimal;

public class PaymentRequest {
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private String paymentMethod;
    
    // Constructors
    public PaymentRequest() {}
    
    public PaymentRequest(Long orderId, Long productId, Integer quantity, BigDecimal amount, String paymentMethod) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }
    
    // Getters and Setters
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
