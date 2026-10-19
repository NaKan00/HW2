package ru.hw.marketplace.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.hw.marketplace.entity.UserEntity;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.exception.BusinessException;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-minutes}") long accessMinutes,
                      @Value("${app.jwt.refresh-days}") long refreshDays) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        if (accessMinutes <= 0 || refreshDays <= 0) throw new IllegalArgumentException("Token lifetimes must be positive");
        this.accessMinutes = accessMinutes; this.refreshDays = refreshDays;
    }
    public TokenResponse tokens(UserEntity user) {
        return new TokenResponse(issue(user, "access", Duration.ofMinutes(accessMinutes)), issue(user, "refresh", Duration.ofDays(refreshDays)));
    }
    private String issue(UserEntity user, String type, Duration duration) {
        Instant now = Instant.now();
        return Jwts.builder().issuer("marketplace").subject(user.id.toString()).claim("role", user.role.name()).claim("type", type)
            .id(UUID.randomUUID().toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plus(duration))).signWith(key).compact();
    }
    public CurrentUser parse(String token, boolean refresh) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer("marketplace").require("type", refresh ? "refresh" : "access").build().parseSignedClaims(token).getPayload();
            if (c.getSubject() == null || c.getExpiration() == null || c.get("role", String.class) == null)
                throw new IllegalArgumentException("Missing required claims");
            return new CurrentUser(UUID.fromString(c.getSubject()), Role.valueOf(c.get("role", String.class)));
        } catch (ExpiredJwtException e) {
            throw new BusinessException(refresh ? ErrorCode.REFRESH_TOKEN_INVALID : ErrorCode.TOKEN_EXPIRED, "Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(refresh ? ErrorCode.REFRESH_TOKEN_INVALID : ErrorCode.TOKEN_INVALID, "Invalid token");
        }
    }
}
