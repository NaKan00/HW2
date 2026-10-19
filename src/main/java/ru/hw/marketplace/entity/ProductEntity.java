package ru.hw.marketplace.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import ru.hw.marketplace.generated.model.*;

@Entity
@Table(name = "products")
public class ProductEntity extends BaseEntity {
    @Column(nullable = false) public String name;
    @Column(length = 4000) public String description;
    @Column(nullable = false, precision = 15, scale = 2) public BigDecimal price;
    @Column(nullable = false) public int stock;
    @Column(nullable = false, length = 100) public String category;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public ProductStatus status;
    @Column(nullable = false) public UUID sellerId;
}
