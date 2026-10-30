package com.gharse.cart;

import com.gharse.catalog.Product;
import com.gharse.catalog.ProductRepository;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import com.gharse.catalog.FulfilmentType;
import com.gharse.catalog.EtaCalculator;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartService {

    /** Sanity cap per cart line; stops integer overflow on repeated adds. */
    public static final int MAX_QUANTITY_PER_ITEM = 1000;

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final SellerProfileRepository sellers;
    private final EtaCalculator etaCalculator;

    public CartService(CartRepository carts, CartItemRepository items,
                       ProductRepository products, SellerProfileRepository sellers,
                       EtaCalculator etaCalculator) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.sellers = sellers;
        this.etaCalculator = etaCalculator;
    }

    @Transactional(readOnly = true)
    public CartView getCart(Long userId) {
        return carts.findByUserId(userId).map(this::toView).orElseGet(CartView::empty);
    }

    @Transactional
    public CartView addItem(Long userId, Long productId, int quantity) {
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        Cart cart = lockOrCreateCart(userId);

        Long cartSeller = cart.getSellerProfileId();
        if (cartSeller != null && !cartSeller.equals(product.getSellerProfileId())) {
            if (items.countByCartId(cart.getId()) > 0) {
                throw new ConflictException(
                        "Your cart already has items from " + sellerName(cartSeller)
                                + ". Clear your cart before adding items from another seller.");
            }
            cart.releaseLock(); // stale lock on an empty cart
        }

        Optional<CartItem> existing = items.findByCartIdAndProductId(cart.getId(), productId);
        if (existing.isPresent()) {
            long total = (long) existing.get().getQuantity() + quantity;
            if (total > MAX_QUANTITY_PER_ITEM) {
                throw new ConflictException("Maximum quantity per item is " + MAX_QUANTITY_PER_ITEM + ".");
            }
            existing.get().changeQuantity((int) total);
        } else {
            items.save(new CartItem(cart.getId(), productId, quantity));
        }

        if (cart.getSellerProfileId() == null) {
            cart.lockTo(product.getSellerProfileId());
        }
        return toView(cart);
    }

    @Transactional
    public CartView updateQuantity(Long userId, Long productId, int quantity) {
        Cart cart = carts.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        item.changeQuantity(quantity);
        return toView(cart);
    }

    @Transactional
    public CartView removeItem(Long userId, Long productId) {
        Cart cart = carts.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        items.delete(item);
        if (items.countByCartId(cart.getId()) == 0) {
            cart.releaseLock();
        }
        return toView(cart);
    }

    /** Clears the cart and releases the single-seller lock. Idempotent. */
    @Transactional
    public CartView clear(Long userId) {
        carts.findByUserIdForUpdate(userId).ifPresent(cart -> {
            items.deleteByCartId(cart.getId());
            cart.releaseLock();
        });
        return CartView.empty();
    }

    private Cart lockOrCreateCart(Long userId) {
        return carts.findByUserIdForUpdate(userId)
                .orElseGet(() -> carts.save(new Cart(userId)));
    }

    private String sellerName(Long sellerProfileId) {
        return sellers.findById(sellerProfileId)
                .map(SellerProfile::getBusinessName)
                .orElse("another seller");
    }

    private CartView toView(Cart cart) {
        List<CartItem> rows = items.findByCartIdOrderByIdAsc(cart.getId());
        Map<Long, Product> byId = products
                .findAllById(rows.stream().map(CartItem::getProductId).toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartView.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (CartItem row : rows) {
            Product p = byId.get(row.getProductId());
            if (p == null) {
                throw new IllegalStateException("Cart item references a missing product: " + row.getProductId());
            }
            BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(row.getQuantity()));
            LocalDate eta = null;
            if (p.getFulfilmentType() == FulfilmentType.MADE_TO_ORDER) {
                eta = etaCalculator.estimatedReadyDate(p, row.getQuantity());
            }
            lines.add(new CartView.Line(p.getId(), p.getName(), p.getPrice(),
                    row.getQuantity(), lineTotal, p.isActive(), eta));
            if (p.isActive()) {
                subtotal = subtotal.add(lineTotal);
            }
        }
        Long sellerId = cart.getSellerProfileId();
        String name = sellerId == null ? null : sellerName(sellerId);
        return new CartView(sellerId, name, lines, subtotal);
    }
}