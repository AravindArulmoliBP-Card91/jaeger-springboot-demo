package com.example.inventory.service;

import com.example.inventory.entity.Inventory;
import com.example.inventory.repository.InventoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncInventoryUpdateService {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncInventoryUpdateService.class);
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Async inventory restock notification
     * Demonstrates async database operations with tracing
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> scheduleRestockNotification(Long productId, Integer threshold) {
        try {
            // Simulate processing delay
            Thread.sleep(200);
            
            // Check current inventory - database operation traced automatically
            Inventory inventory = inventoryRepository.findByProductId(productId).orElse(null);
            
            if (inventory != null && inventory.getQuantityAvailable() < threshold) {
                // Cache restock notification
                String notificationKey = "restock:notification:" + productId;
                String notificationData = String.format(
                    "{\"productId\":%d,\"currentStock\":%d,\"threshold\":%d,\"status\":\"PENDING\"}", 
                    productId, inventory.getQuantityAvailable(), threshold);
                
                redisTemplate.opsForValue().set(notificationKey, notificationData, Duration.ofDays(7));
                
                // Update notification counter
                redisTemplate.opsForValue().increment("restock:notifications:count", 1);
                
                logger.info("📦 Restock notification scheduled for product " + productId);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Restock notification failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Async inventory analytics update
     * Shows parallel async operations with database and cache
     */
    @Async("taskExecutor")
    public CompletableFuture<String> updateInventoryAnalytics(Long productId) {
        try {
            // Simulate analytics processing
            Thread.sleep(600);
            
            // Get inventory data - traced automatically
            Inventory inventory = inventoryRepository.findByProductId(productId).orElse(null);
            
            if (inventory != null) {
                // Calculate analytics
                double turnoverRate = (double) inventory.getReservedQuantity() / 
                                    (inventory.getQuantityAvailable() + inventory.getReservedQuantity());
                
                // Cache analytics data
                String analyticsKey = "analytics:inventory:" + productId;
                String analyticsData = String.format(
                    "{\"productId\":%d,\"turnoverRate\":%.2f,\"totalStock\":%d,\"lastUpdated\":%d}", 
                    productId, turnoverRate, 
                    inventory.getQuantityAvailable() + inventory.getReservedQuantity(),
                    System.currentTimeMillis());
                
                redisTemplate.opsForValue().set(analyticsKey, analyticsData, Duration.ofHours(6));
                
                // Update daily analytics summary
                String dailyKey = "analytics:daily:" + java.time.LocalDate.now();
                redisTemplate.opsForHash().increment(dailyKey, "products_analyzed", 1);
                redisTemplate.expire(dailyKey, Duration.ofDays(30));
                
                String result = "Analytics updated for product " + productId + " (turnover: " + 
                              String.format("%.1f%%", turnoverRate * 100) + ")";
                logger.info("📊 " + result);
                
                return CompletableFuture.completedFuture(result);
            }
            
            return CompletableFuture.completedFuture("Product not found: " + productId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Analytics update failed", e);
        }
    }
    
    /**
     * Async supplier notification
     * Demonstrates fire-and-forget async operations
     */
    @Async("taskExecutor")
    public void notifySupplierLowStock(Long productId, String supplierEmail) {
        try {
            // Simulate supplier notification processing
            Thread.sleep(300);
            
            // Cache supplier notification
            String supplierKey = "supplier:notification:" + productId + ":" + System.currentTimeMillis();
            String supplierData = String.format(
                "{\"productId\":%d,\"supplierEmail\":\"%s\",\"type\":\"LOW_STOCK\",\"timestamp\":%d}", 
                productId, supplierEmail, System.currentTimeMillis());
            
            redisTemplate.opsForValue().set(supplierKey, supplierData, Duration.ofDays(7));
            
            // Update supplier notification history
            String historyKey = "supplier:history:" + supplierEmail;
            redisTemplate.opsForList().leftPush(historyKey, supplierData);
            redisTemplate.expire(historyKey, Duration.ofDays(90));
            
            logger.info("📧 Supplier notified: " + supplierEmail + " for product " + productId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Failed to notify supplier: " + e.getMessage());
        }
    }
}
