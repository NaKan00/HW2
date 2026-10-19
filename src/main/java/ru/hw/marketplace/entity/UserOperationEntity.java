package ru.hw.marketplace.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "user_operations")
public class UserOperationEntity {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public UUID userId;
    @Column(nullable = false) public String operationType;
    @Column(nullable = false, updatable = false) public OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
}
