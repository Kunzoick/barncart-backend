package com.zoick.farmmarket.domain.produce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProduceRepository extends JpaRepository<Produce, UUID> {
    // Fetch join — loads category in single query, prevents N+1
    @Query("""
        SELECT p FROM Produce p
        JOIN FETCH p.category
        WHERE p.active = true
        """)
    List<Produce> findAllActiveWithCategory();

    // Fetch join — filtered by category, loads category in single query
    @Query("""
        SELECT p FROM Produce p
        JOIN FETCH p.category
        WHERE p.category.id = :categoryId
        AND p.active = true
        """)
    List<Produce> findAllByCategoryIdActiveWithCategory(
            @Param("categoryId") UUID categoryId);

    // Fetch join — single item lookup with category loaded
    @Query("""
        SELECT p FROM Produce p
        JOIN FETCH p.category
        WHERE p.id = :id
        """)
    Optional<Produce> findByIdWithCategory(@Param("id") UUID id);
}
