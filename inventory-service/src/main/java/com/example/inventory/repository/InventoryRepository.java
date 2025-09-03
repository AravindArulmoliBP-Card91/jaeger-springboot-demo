package com.example.inventory.repository;

import com.example.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity + :quantity WHERE i.productId = :productId AND i.quantityAvailable >= :quantity")
    int reserveQuantity(@Param("productId") Long productId, @Param("quantity") Integer quantity);
    
    @Query("SELECT CASE WHEN i.quantityAvailable >= :quantity THEN true ELSE false END FROM Inventory i WHERE i.productId = :productId")
    boolean isQuantityAvailable(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
