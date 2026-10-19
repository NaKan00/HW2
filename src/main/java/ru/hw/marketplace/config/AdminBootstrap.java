package ru.hw.marketplace.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hw.marketplace.repository.UserRepository;
import ru.hw.marketplace.entity.UserEntity;
import ru.hw.marketplace.generated.model.Role;
import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
        @Value("${app.bootstrap-admin.email}") String email, @Value("${app.bootstrap-admin.password}") String password) {
        this.users = users; this.encoder = encoder; this.email = email; this.password = password;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Provide admin email and a password of 8-72 UTF-8 bytes");
        String normalized = email.toLowerCase(Locale.ROOT);
        var existing = users.findByEmail(normalized);
        if (existing.isPresent()) {
            if (existing.get().role != Role.ADMIN) throw new IllegalStateException("Admin email already belongs to a non-admin user");
            return;
        }
        UserEntity u = new UserEntity(); u.email = normalized; u.passwordHash = encoder.encode(password); u.role = Role.ADMIN;
        users.save(u);
    }
}
