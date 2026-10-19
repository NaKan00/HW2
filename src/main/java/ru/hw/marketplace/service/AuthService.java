package ru.hw.marketplace.service;

import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hw.marketplace.repository.UserRepository;
import ru.hw.marketplace.entity.UserEntity;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.security.JwtService;
import ru.hw.marketplace.exception.BusinessException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) { this.users = users; this.encoder = encoder; this.jwt = jwt; }
    @Transactional public UserResponse register(RegisterRequest request) {
        UserEntity u = new UserEntity(); u.email = request.getEmail().toLowerCase(Locale.ROOT);
        u.passwordHash = encoder.encode(request.getPassword());
        u.role = request.getRole() == null ? Role.USER : Role.valueOf(request.getRole().getValue());
        return ApiMapper.user(users.saveAndFlush(u));
    }
    @Transactional(readOnly = true) public TokenResponse login(LoginRequest request) {
        UserEntity u = users.findByEmail(request.getEmail().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "Invalid email or password"));
        if (!encoder.matches(request.getPassword(), u.passwordHash)) throw new BusinessException(ErrorCode.TOKEN_INVALID, "Invalid email or password");
        return jwt.tokens(u);
    }
    @Transactional(readOnly = true) public TokenResponse refresh(RefreshRequest request) {
        var principal = jwt.parse(request.getRefreshToken(), true);
        UserEntity u = users.findById(principal.id()).orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Unknown user"));
        return jwt.tokens(u);
    }
}
