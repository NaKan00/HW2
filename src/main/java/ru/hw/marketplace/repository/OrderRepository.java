package ru.hw.marketplace.repository;

import ru.hw.marketplace.entity.OrderEntity;
import ru.hw.marketplace.generated.model.OrderStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    boolean existsByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses);
    @Query("select o.userId from OrderEntity o where o.id = :id")
    Optional<UUID> findOwner(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") UUID id);
}
