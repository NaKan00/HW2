package ru.hw.marketplace.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false, updatable = false) public OffsetDateTime createdAt;
    @Column(nullable = false) public OffsetDateTime updatedAt;
    @PrePersist public void createTimestamps() { createdAt = OffsetDateTime.now(ZoneOffset.UTC); updatedAt = createdAt; }
    @PreUpdate public void updateTimestamp() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
