package com.zoick.farmmarket.domain.produce;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {
    @Query(value = """
    SELECT l FROM Listing l
    JOIN FETCH l.batch b
    JOIN FETCH b.produce p
    JOIN FETCH p.category
    WHERE l.active = true
    AND b.status = 'ACTIVE'
    AND b.expiryDate >= CURRENT_DATE
    """,
            countQuery = """
    SELECT COUNT(l) FROM Listing l
    JOIN l.batch b
    WHERE l.active = true
    AND b.status = 'ACTIVE'
    AND b.expiryDate >= CURRENT_DATE
    """)
    Page<Listing> findAllActiveWithDetails(Pageable pageable);
    //single listing fetch
    @Query("""
        SELECT l FROM Listing l
        JOIN FETCH l.batch b
        JOIN FETCH b.produce p
        JOIN FETCH p.category
        WHERE l.id = :id
        """)
    Optional<Listing> findByIdWithDetails(@Param("id") UUID id);
    // Cache-bypassing fresh load — used after atomic SQL updates
    // @Modifying queries bypass Hibernate cache — this forces fresh DB read
    @QueryHints(@QueryHint(
            name = "jakarta.persistence.cache.retrieveMode",
            value = "BYPASS"))
    @Query("""
        SELECT l FROM Listing l
        JOIN FETCH l.batch b
        JOIN FETCH b.produce p
        WHERE b.id = :batchId
        """)
    Optional<Listing> findByBatchIdWithFreshBatch(@Param("batchId") UUID batchId);
    Optional<Listing> findByBatchId(UUID batchId);
    boolean existsByBatchId(UUID batchId);
}