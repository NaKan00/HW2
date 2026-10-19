package ru.hw.marketplace.service;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hw.marketplace.entity.ProductEntity;
import ru.hw.marketplace.repository.ProductRepository;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.security.CurrentUser;
import ru.hw.marketplace.exception.BusinessException;

@Service
public class ProductService {
    private final ProductRepository products;
    public ProductService(ProductRepository products) { this.products = products; }
    @Transactional public ProductResponse create(ProductCreate r) {
        ProductEntity p = new ProductEntity(); p.sellerId = CurrentUser.get().id();
        p.name = r.getName(); p.description = r.getDescription(); p.price = r.getPrice(); p.stock = r.getStock(); p.category = r.getCategory(); p.status = r.getStatus();
        return ApiMapper.product(products.saveAndFlush(p));
    }
    @Transactional(readOnly = true) public ProductResponse get(UUID id) { return ApiMapper.product(find(id)); }
    @Transactional(readOnly = true) public ProductPage list(int page, int size, ProductStatus status, String category) {
        Specification<ProductEntity> filter = (root, query, cb) -> cb.conjunction();
        if (status != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (category != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("category"), category));
        var result = products.findAll(filter, PageRequest.of(page, size, Sort.by("createdAt", "id")));
        return new ProductPage(result.getContent().stream().map(ApiMapper::product).toList(), result.getTotalElements(), page, size);
    }
    @Transactional public ProductResponse update(UUID id, ProductUpdate r) {
        ProductEntity p = locked(id); own(p);
        p.name = r.getName(); p.description = r.getDescription(); p.price = r.getPrice(); p.stock = r.getStock(); p.category = r.getCategory(); p.status = r.getStatus();
        products.flush(); return ApiMapper.product(p);
    }
    @Transactional public void archive(UUID id) {
        ProductEntity p = locked(id); own(p); p.status = ProductStatus.ARCHIVED;
    }
    private ProductEntity find(UUID id) { return products.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found")); }
    private ProductEntity locked(UUID id) { return products.lockById(id).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found")); }
    private void own(ProductEntity p) {
        var user = CurrentUser.get();
        if (!user.admin() && !p.sellerId.equals(user.id())) throw new BusinessException(ErrorCode.ACCESS_DENIED, "Product belongs to another seller");
    }
}
