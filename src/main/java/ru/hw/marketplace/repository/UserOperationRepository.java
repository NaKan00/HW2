package ru.hw.marketplace.repository;

import ru.hw.marketplace.entity.UserOperationEntity;
import ru.hw.marketplace.generated.model.OrderStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface UserOperationRepository extends JpaRepository<UserOperationEntity, UUID> {
    Optional<UserOperationEntity> findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(UUID userId, String operationType);
}
