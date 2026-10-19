package ru.hw.marketplace.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import ru.hw.marketplace.generated.model.*;

@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {
    @Column(nullable = false, unique = true) public String email;
    @Column(nullable = false) public String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Role role;
}
