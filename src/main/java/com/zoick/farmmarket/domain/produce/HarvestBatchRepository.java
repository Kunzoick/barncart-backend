package com.zoick.farmmarket.domain.produce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface HarvestBatchRepository extends JpaRepository<HarvestBatch, UUID> {
    List<HarvestBatch> findAllByStatus(BatchStatus status);
    List<HarvestBatch> findAllByProduceId(UUID produceId);
    boolean existsByProduceIdAndStatus(UUID produceId, BatchStatus status);
    // Atomic stock deduction — returns 1 if successful, 0 if insufficient stock
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE HarvestBatch h
        SET h.quantityAvailable = h.quantityAvailable - :quantity
        WHERE h.id = :batchId
        AND h.quantityAvailable >= :quantity
        """)
    int deductStock(@Param("batchId") UUID batchId,
                    @Param("quantity") BigDecimal quantity);

    // Atomic stock return — used on expiry, cancellation, payment failure
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE HarvestBatch h
        SET h.quantityAvailable = h.quantityAvailable + :quantity
        WHERE h.id = :batchId
        AND h.quantityAvailable + :quantity <= h.quantityOriginal
        """)
    int returnStock(@Param("batchId") UUID batchId,
                    @Param("quantity") BigDecimal quantity);
    // atomic for restock
    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE HarvestBatch h
    SET h.quantityAvailable = h.quantityAvailable + :quantity,
        h.quantityOriginal = h.quantityOriginal + :quantity
    WHERE h.id = :batchId
    AND h.status != 'CANCELLED'
    """)
    int addStock(@Param("batchId") UUID batchId,
                 @Param("quantity") BigDecimal quantity);
}