package ru.hw.marketplace.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import ru.hw.marketplace.generated.model.*;

@Entity
@Table(name = "promo_codes")
public class PromoEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 20) public String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public DiscountType discountType;
    @Column(nullable = false, precision = 15, scale = 2) public BigDecimal discountValue;
    @Column(nullable = false, precision = 15, scale = 2) public BigDecimal minOrderAmount;
    @Column(nullable = false) public int maxUses;
    @Column(nullable = false) public int currentUses;
    @Column(nullable = false) public OffsetDateTime validFrom;
    @Column(nullable = false) public OffsetDateTime validUntil;
    @Column(nullable = false) public boolean active;
}
