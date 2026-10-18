package com.gharse.catalog;

import com.gharse.catalog.dto.ProductResponse;
import com.gharse.catalog.dto.ProductSearchResult;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderStatus;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CategoryRepository categories;
    private final SellerProfileRepository sellers;
    private final OrderItemRepository orderItems;

    public ProductQueryService(ProductRepository products, ProductImageRepository images,
                               CategoryRepository categories, SellerProfileRepository sellers,
                               OrderItemRepository orderItems) {
        this.products = products;
        this.images = images;
        this.categories = categories;
        this.sellers = sellers;
        this.orderItems = orderItems;
    }

    /** Filters, Haversine radius, and sort. Pagination is applied after sorting in memory. */
    public Page<ProductSearchResult> search(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                            Double lat, Double lng, Double radiusKm,
                                            String sort, int page, int pageSize) {
        List<Product> active = products.findByActiveTrue();

        String keyword = q == null ? "" : q.trim().toLowerCase();
        Map<Long, Category> cats = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, SellerProfile> sellerMap = sellers.findAll().stream()
                .collect(Collectors.toMap(SellerProfile::getId, Function.identity()));

        List<ProductSearchResult> rows = active.stream()
                .filter(p -> keyword.isEmpty() || p.getName().toLowerCase().contains(keyword))
                .filter(p -> categoryId == null || categoryId.equals(p.getCategoryId()))
                .filter(p -> minPrice == null || p.getPrice().compareTo(minPrice) >= 0)
                .filter(p -> maxPrice == null || p.getPrice().compareTo(maxPrice) <= 0)
                .map(p -> toSearchRow(p, cats, sellerMap, lat, lng))
                .filter(r -> radiusKm == null || lat == null || lng == null
                        || (r.distanceKm() != null && r.distanceKm() <= radiusKm))
                .sorted(comparator(sort, lat != null && lng != null))
                .toList();

        int from = Math.min((page - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return new Page<>(rows.subList(from, to), page, pageSize, rows.size());
    }

    public ProductResponse getById(Long id) {
        Product p = products.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return toDetail(p);
    }

    /**
     * READY_STOCK: { inStock, quantityRemaining }.
     * MADE_TO_ORDER: { estimatedReadyDate }, based on the live backlog of CONFIRMED and IN_PRODUCTION orders.
     */
    public Map<String, Object> eta(Long id) {
        Product p = products.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));

        if (p.getFulfilmentType() == FulfilmentType.READY_STOCK) {
            int qty = p.getInventoryQty() == null ? 0 : p.getInventoryQty();
            return Map.of("inStock", qty > 0, "quantityRemaining", qty);
        }
        long backlogUnits = orderItems.sumBacklogUnits(p.getId(),
                List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION));
        long minutes = (backlogUnits + 1) * p.getProductionTimeMinutesPerUnit();
        LocalDate ready = LocalDate.now().plusDays((minutes + 60 * 24 - 1) / (60 * 24));
        return Map.of("estimatedReadyDate", ready.toString());
    }

    /** Reviews belong to module H. Returns an empty list until that module ships. */
    public List<Object> reviews(Long id) {
        products.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return List.of();
    }

    public List<Category> categories() {
        return categories.findAllByOrderByNameAsc();
    }

    // ---------------------------------------------------------------

    private ProductSearchResult toSearchRow(Product p, Map<Long, Category> cats,
                                            Map<Long, SellerProfile> sellerMap, Double lat, Double lng) {
        SellerProfile s = sellerMap.get(p.getSellerProfileId());
        Category c = cats.get(p.getCategoryId());
        String image = images.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
                .findFirst().map(ProductImage::getImageUrl).orElse(null);

        Double distance = null;
        if (lat != null && lng != null && s != null) {
            distance = HaversineUtil.distanceKm(lat, lng,
                    s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
        }
        boolean inStock = p.getFulfilmentType() == FulfilmentType.MADE_TO_ORDER
                || (p.getInventoryQty() != null && p.getInventoryQty() > 0);

        return new ProductSearchResult(
                p.getId(), p.getName(), p.getPrice(),
                p.getCategoryId(), c == null ? "" : c.getName(),
                p.getSellerProfileId(), s == null ? "" : s.getBusinessName(),
                p.getFulfilmentType().name(), inStock, image,
                p.isVerified(), p.getAverageRating(), p.getReviewCount(), distance);
    }

    /** Verified first (FR8); then nearest when location is given, else newest. */
    private Comparator<ProductSearchResult> comparator(String sort, boolean hasLocation) {
        Comparator<ProductSearchResult> verifiedFirst = (a, b) -> Boolean.compare(b.verified(), a.verified());
        if (sort == null || sort.isBlank() || "recommended".equals(sort)) {
            if (hasLocation) {
                return verifiedFirst.thenComparing(ProductSearchResult::distanceKm,
                        Comparator.nullsLast(Comparator.naturalOrder()));
            }
            return verifiedFirst.thenComparing(ProductSearchResult::id, Comparator.reverseOrder());
        }
        return switch (sort) {
            case "newest" -> Comparator.comparing(ProductSearchResult::id, Comparator.reverseOrder());
            case "price_asc" -> Comparator.comparing(ProductSearchResult::price);
            case "price_desc" -> Comparator.comparing(ProductSearchResult::price, Comparator.reverseOrder());
            default -> verifiedFirst;
        };
    }

    private ProductResponse toDetail(Product p) {
        SellerProfile s = sellers.findById(p.getSellerProfileId()).orElse(null);
        Category c = categories.findById(p.getCategoryId()).orElse(null);
        List<String> urls = images.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
                .map(ProductImage::getImageUrl).toList();
        boolean inStock = p.getFulfilmentType() == FulfilmentType.MADE_TO_ORDER
                || (p.getInventoryQty() != null && p.getInventoryQty() > 0);
        return new ProductResponse(
                p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                p.getCategoryId(), c == null ? "" : c.getName(),
                p.getSellerProfileId(), s == null ? "" : s.getBusinessName(),
                p.getFulfilmentType(), p.getInventoryQty(), p.getProductionTimeMinutesPerUnit(),
                p.isSupportsSelfDelivery(), p.isSupportsPickup(), p.isSupportsPostal(),
                p.getDeliveryRadiusKm(), p.isVerified(), p.getAverageRating(), p.getReviewCount(),
                p.isActive(), inStock, urls.isEmpty() ? null : urls.get(0), urls);
    }

    /** Simple page wrapper returned to the controller. */
    public record Page<T>(List<T> items, int page, int pageSize, int totalItems) {
        public int totalPages() {
            return Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        }
    }
}