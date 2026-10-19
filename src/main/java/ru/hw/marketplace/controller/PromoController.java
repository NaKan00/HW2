package ru.hw.marketplace.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.hw.marketplace.generated.api.PromoCodesApi;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.service.PromoService;

@RestController
public class PromoController implements PromoCodesApi {
    private final PromoService promos;
    public PromoController(PromoService promos) { this.promos = promos; }
    @Override @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<PromoResponse> createPromo(PromoCreate r) {
        if (r.getValidUntil().isBefore(r.getValidFrom())) ContractChecks.invalid("valid_until", "must be on or after valid_from");
        return ResponseEntity.status(201).body(promos.create(r));
    }
}
