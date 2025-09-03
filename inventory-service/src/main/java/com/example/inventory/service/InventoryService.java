package com.example.inventory.service;

import com.example.inventory.dto.ReservationRequest;
import com.example.inventory.dto.ReservationResponse;
import com.example.inventory.entity.Inventory;
import com.example.inventory.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Service
public class InventoryService {
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Transactional
    public ReservationResponse reserveInventory(ReservationRequest request) {
        Long productId = request.getProductId();
        Integer quantity = request.getQuantity();
        
        // Cache key for inventory check
        String cacheKey = "inventory:check:" + productId;
        
        // Check Redis cache first
        String cachedResult = (String) redisTemplate.opsForValue().get(cacheKey);
        if ("INSUFFICIENT".equals(cachedResult)) {
            return new ReservationResponse(false, "Insufficient inventory (cached result)");
        }
        
        // Find inventory by product ID
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(productId);
        if (inventoryOpt.isEmpty()) {
            // Cache the negative result
            redisTemplate.opsForValue().set(cacheKey, "NOT_FOUND", Duration.ofMinutes(5));
            return new ReservationResponse(false, "Product not found");
        }
        
        Inventory inventory = inventoryOpt.get();
        
        // Check if quantity is available
        if (inventory.getQuantityAvailable() < quantity) {
            // Cache the insufficient inventory result
            redisTemplate.opsForValue().set(cacheKey, "INSUFFICIENT", Duration.ofMinutes(2));
            return new ReservationResponse(false, "Insufficient inventory available");
        }
        
        // Reserve the quantity
        int updated = inventoryRepository.reserveQuantity(productId, quantity);
        if (updated > 0) {
            // Update available quantity
            inventory.setQuantityAvailable(inventory.getQuantityAvailable() - quantity);
            inventoryRepository.save(inventory);
            
            // Calculate total amount
            BigDecimal totalAmount = inventory.getUnitPrice().multiply(new BigDecimal(quantity));
            
            // Cache successful reservation
            redisTemplate.opsForValue().set("inventory:reserved:" + productId, quantity.toString(), Duration.ofMinutes(30));
            
            // Remove any negative cache entries
            redisTemplate.delete(cacheKey);
            
            return new ReservationResponse(
                true, 
                "Inventory reserved successfully", 
                productId, 
                quantity, 
                inventory.getUnitPrice(), 
                totalAmount
            );
        } else {
            return new ReservationResponse(false, "Failed to reserve inventory");
        }
    }
    
    public Inventory getInventoryByProductId(Long productId) {
        // Check cache first
        String cacheKey = "inventory:product:" + productId;
        Inventory cachedInventory = (Inventory) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedInventory != null) {
            return cachedInventory;
        }
        
        // Fetch from database
        Optional<Inventory> inventory = inventoryRepository.findByProductId(productId);
        if (inventory.isPresent()) {
            // Cache the result for 10 minutes
            redisTemplate.opsForValue().set(cacheKey, inventory.get(), Duration.ofMinutes(10));
            return inventory.get();
        }
        
        return null;
    }
}
