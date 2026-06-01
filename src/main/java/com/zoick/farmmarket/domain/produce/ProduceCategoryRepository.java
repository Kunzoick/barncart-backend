package com.zoick.farmmarket.domain.produce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProduceCategoryRepository extends JpaRepository<ProduceCategory, UUID> {
    Optional<ProduceCategory> findByName(String name);
    boolean existsByName(String name);
}
