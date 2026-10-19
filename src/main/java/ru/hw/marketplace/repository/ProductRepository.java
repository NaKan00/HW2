package ru.hw.marketplace.repository;

import ru.hw.marketplace.entity.ProductEntity;
import ru.hw.marketplace.generated.model.OrderStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from ProductEntity p where p.id = :id")
    Optional<ProductEntity> lockById(@Param("id") UUID id);
}
