package ru.hw.marketplace.repository;

import ru.hw.marketplace.entity.PromoEntity;
import ru.hw.marketplace.generated.model.OrderStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PromoRepository extends JpaRepository<PromoEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PromoEntity p where p.code = :code")
    Optional<PromoEntity> lockByCode(@Param("code") String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PromoEntity p where p.id = :id")
    Optional<PromoEntity> lockById(@Param("id") UUID id);
}
