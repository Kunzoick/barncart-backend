package com.zoick.farmmarket.domain.cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    @Query("""
        SELECT ci FROM CartItem ci
        JOIN FETCH ci.listing l
        JOIN FETCH l.batch b
        JOIN FETCH b.produce p
        WHERE ci.cart.id = :cartId
        """)
    List<CartItem> findAllByCartIdWithDetails(@Param("cartId") UUID cartId);
    Optional<CartItem> findByCartIdAndListingId(UUID cartId, UUID listingId);
    // Ownership check — single indexed query, no lazy loading chain
    boolean existsByIdAndCartUserId(UUID cartItemId, UUID userId);
    void deleteAllByCartId(UUID cartId);
    // Combined ownership check + fetch — eliminates double DB hit in updateItem/removeItem
    @Query("""
        SELECT ci FROM CartItem ci
        JOIN FETCH ci.cart c
        JOIN FETCH ci.listing l
        JOIN FETCH l.batch b
        JOIN FETCH b.produce p
        WHERE ci.id = :itemId
        AND c.user.id = :userId
        """)
    Optional<CartItem> findByIdAndUserId(
            @Param("itemId") UUID itemId,
            @Param("userId") UUID userId);
}