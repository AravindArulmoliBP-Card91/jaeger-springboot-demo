package com.example.order.dto;

import java.math.BigDecimal;

public class OrderRequest {
    private String customerName;
    private Long productId;
    private Integer quantity;
    private String paymentMethod;
    
    // Constructors
    public OrderRequest() {}
    
    public OrderRequest(String customerName, Long productId, Integer quantity, String paymentMethod) {
        this.customerName = customerName;
        this.productId = productId;
        this.quantity = quantity;
        this.paymentMethod = paymentMethod;
    }
    
    // Getters and Setters
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
