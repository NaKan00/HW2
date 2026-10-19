package ru.hw.marketplace.controller;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.hw.marketplace.generated.api.ProductsApi;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.service.ProductService;

@RestController
public class ProductController implements ProductsApi {
    private final ProductService products;
    public ProductController(ProductService products) { this.products = products; }
    @Override @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(ProductCreate r) { return ResponseEntity.status(201).body(products.create(r)); }
    @Override public ResponseEntity<ProductResponse> getProduct(UUID id) { return ResponseEntity.ok(products.get(id)); }
    @Override public ResponseEntity<ProductPage> listProducts(Integer page, Integer size, ProductStatus status, String category) { return ResponseEntity.ok(products.list(page, size, status, category)); }
    @Override @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(UUID id, ProductUpdate r) { return ResponseEntity.ok(products.update(id, r)); }
    @Override @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<Void> deleteProduct(UUID id) { products.archive(id); return ResponseEntity.noContent().build(); }
}
