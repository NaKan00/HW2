package ru.hw.marketplace.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.hw.marketplace.generated.model.Role;

public record CurrentUser(UUID id, Role role) {
    public static CurrentUser get() {
        return (CurrentUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    public boolean admin() { return role == Role.ADMIN; }
}
