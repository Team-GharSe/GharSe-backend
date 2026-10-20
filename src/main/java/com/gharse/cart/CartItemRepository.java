package com.gharse.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    long countByCartId(Long cartId);

    /**
     * flushAutomatically only. Do NOT add clearAutomatically: it would detach the locked Cart
     * entity and silently lose the lock release that follows in the same transaction.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from CartItem i where i.cartId = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);
}