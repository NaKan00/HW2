package ru.hw.marketplace.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.hw.marketplace.generated.api.AuthApi;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.service.AuthService;

@RestController
public class AuthController implements AuthApi {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @Override public ResponseEntity<UserResponse> register(RegisterRequest r) { ContractChecks.password(r.getPassword()); return ResponseEntity.status(201).body(auth.register(r)); }
    @Override public ResponseEntity<TokenResponse> login(LoginRequest r) { ContractChecks.password(r.getPassword()); return ResponseEntity.ok(auth.login(r)); }
    @Override public ResponseEntity<TokenResponse> refresh(RefreshRequest r) { return ResponseEntity.ok(auth.refresh(r)); }
}
