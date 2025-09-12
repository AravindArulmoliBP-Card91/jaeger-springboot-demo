package com.example.inventory.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.dto.ReservationRequest;
import com.example.inventory.dto.ReservationResponse;
import com.example.inventory.entity.Inventory;
import com.example.inventory.repository.InventoryRepository;

@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private AsyncInventoryUpdateService asyncInventoryUpdateService;
    
    @Transactional
    public ReservationResponse reserveInventory(ReservationRequest request) {
        Long productId = request.getProductId();
        Integer quantity = request.getQuantity();
        
        logger.info("Starting inventory reservation for productId: {} and quantity: {}", productId, quantity);
        
        // Cache key for inventory check
        String cacheKey = "inventory:check:" + productId;
        
        // Check Redis cache first
        String cachedResult = (String) redisTemplate.opsForValue().get(cacheKey);
        if ("INSUFFICIENT".equals(cachedResult)) {
            logger.debug("Insufficient inventory found in cache for productId: {}", productId);
            return new ReservationResponse(false, "Insufficient inventory (cached result)");
        }
        
        // Find inventory by product ID
        logger.debug("Looking up inventory for productId: {}", productId);
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(productId);
        if (inventoryOpt.isEmpty()) {
            logger.warn("Product not found for productId: {}", productId);
            // Cache the negative result
            redisTemplate.opsForValue().set(cacheKey, "NOT_FOUND", Duration.ofMinutes(5));
            return new ReservationResponse(false, "Product not found");
        }
        
        Inventory inventory = inventoryOpt.get();
        logger.debug("Found inventory for productId: {} with available quantity: {}", 
                    productId, inventory.getQuantityAvailable());
        
        // Check if quantity is available
        if (inventory.getQuantityAvailable() < quantity) {
            logger.warn("Insufficient inventory for productId: {} - requested: {}, available: {}", 
                       productId, quantity, inventory.getQuantityAvailable());
            // Cache the insufficient inventory result
            redisTemplate.opsForValue().set(cacheKey, "INSUFFICIENT", Duration.ofMinutes(2));
            return new ReservationResponse(false, "Insufficient inventory available");
        }
        
        // Reserve the quantity
        logger.info("Attempting to reserve {} units for productId: {}", quantity, productId);
        int updated = inventoryRepository.reserveQuantity(productId, quantity);
        if (updated > 0) {
            // Update available quantity
            inventory.setQuantityAvailable(inventory.getQuantityAvailable() - quantity);
            inventoryRepository.save(inventory);
            logger.info("Successfully reserved {} units for productId: {} - remaining available: {}", 
                       quantity, productId, inventory.getQuantityAvailable());
            
            // Calculate total amount
            BigDecimal totalAmount = inventory.getUnitPrice().multiply(new BigDecimal(quantity));
            logger.debug("Calculated total amount: {} for quantity: {} at unit price: {}", 
                        totalAmount, quantity, inventory.getUnitPrice());
            
            // Cache successful reservation
            redisTemplate.opsForValue().set("inventory:reserved:" + productId, quantity.toString(), Duration.ofMinutes(30));
            logger.debug("Cached successful reservation for productId: {}", productId);
            
            // Remove any negative cache entries
            redisTemplate.delete(cacheKey);
            
            // Trigger async inventory management operations - traced automatically by OpenTelemetry
            logger.info("Triggering async inventory operations for productId: {}", productId);
            triggerAsyncInventoryOperations(productId, quantity);
            
            return new ReservationResponse(
                true, 
                "Inventory reserved successfully", 
                productId, 
                quantity, 
                inventory.getUnitPrice(), 
                totalAmount
            );
        } else {
            logger.warn("Failed to reserve inventory for productId: {} - database update returned 0 rows", productId);
            return new ReservationResponse(false, "Failed to reserve inventory");
        }
    }
    
    public Inventory getInventoryByProductId(Long productId) {
        logger.debug("Retrieving inventory for productId: {}", productId);
        
        // Check cache first
        String cacheKey = "inventory:product:" + productId;
        Inventory cachedInventory = (Inventory) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedInventory != null) {
            logger.debug("Inventory found in cache for productId: {}", productId);
            return cachedInventory;
        }
        
        // Fetch from database
        logger.debug("Inventory not in cache, fetching from database for productId: {}", productId);
        Optional<Inventory> inventory = inventoryRepository.findByProductId(productId);
        if (inventory.isPresent()) {
            // Cache the result for 10 minutes
            redisTemplate.opsForValue().set(cacheKey, inventory.get(), Duration.ofMinutes(10));
            logger.debug("Cached inventory data for productId: {} for 10 minutes", productId);
            return inventory.get();
        }
        
        logger.debug("No inventory found for productId: {}", productId);
        return null;
    }
    
    /**
     * Trigger async inventory management operations
     * OpenTelemetry will automatically trace these async operations
     */
    private void triggerAsyncInventoryOperations(Long productId, Integer reservedQuantity) {
        // Low stock threshold check - fire and forget
        asyncInventoryUpdateService.scheduleRestockNotification(productId, 10);
        
        // Analytics update - with result handling
        asyncInventoryUpdateService.updateInventoryAnalytics(productId)
            .thenAccept(result -> {
                logger.info("📊 " + result);
            })
            .exceptionally(throwable -> {
                logger.error("❌ Analytics update failed for product: " + productId + 
                                 " - " + throwable.getMessage());
                return null;
            });
        
        // Supplier notification for low stock (fire and forget)
        if (reservedQuantity > 5) { // If large quantity reserved, might need supplier notification
            asyncInventoryUpdateService.notifySupplierLowStock(productId, "supplier@example.com");
        }
    }
}
