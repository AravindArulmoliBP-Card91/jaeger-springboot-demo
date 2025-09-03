package com.example.inventory.controller;

import com.example.inventory.dto.ReservationRequest;
import com.example.inventory.dto.ReservationResponse;
import com.example.inventory.entity.Inventory;
import com.example.inventory.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class InventoryController {
    
    @Autowired
    private InventoryService inventoryService;
    
    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserveInventory(@RequestBody ReservationRequest request) {
        ReservationResponse response = inventoryService.reserveInventory(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/inventory/{productId}")
    public ResponseEntity<Inventory> getInventory(@PathVariable Long productId) {
        Inventory inventory = inventoryService.getInventoryByProductId(productId);
        
        if (inventory != null) {
            return ResponseEntity.ok(inventory);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Inventory Service is healthy");
    }
}
