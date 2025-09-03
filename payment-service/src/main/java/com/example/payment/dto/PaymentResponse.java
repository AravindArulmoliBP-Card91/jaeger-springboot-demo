package com.example.payment.dto;

import java.math.BigDecimal;

public class PaymentResponse {
    private boolean success;
    private String message;
    private Long paymentId;
    private String transactionId;
    private BigDecimal amount;
    private String status;
    
    // Constructors
    public PaymentResponse() {}
    
    public PaymentResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public PaymentResponse(boolean success, String message, Long paymentId, String transactionId, BigDecimal amount, String status) {
        this.success = success;
        this.message = message;
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = status;
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
