package com.gharse.payment;

import com.gharse.cart.Cart;
import com.gharse.cart.CartItem;
import com.gharse.cart.CartItemRepository;
import com.gharse.cart.CartRepository;
import com.gharse.catalog.FulfilmentType;
import com.gharse.catalog.Product;
import com.gharse.catalog.ProductRepository;
import com.gharse.common.exception.BadRequestException;
import com.gharse.common.exception.ConflictException;
import com.gharse.order.FulfilmentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String CURRENCY = "INR";

    private final PaymentRepository payments;
    private final PaymentItemRepository paymentItems;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final RazorpayService razorpay;
    private final TransactionTemplate tx;
    private final String razorpayKeyId;

    public PaymentService(PaymentRepository payments,
                          PaymentItemRepository paymentItems,
                          CartRepository carts,
                          CartItemRepository cartItems,
                          ProductRepository products,
                          RazorpayService razorpay,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.razorpay.key-id:}") String razorpayKeyId) {
        this.payments = payments;
        this.paymentItems = paymentItems;
        this.carts = carts;
        this.cartItems = cartItems;
        this.products = products;
        this.razorpay = razorpay;
        this.tx = new TransactionTemplate(transactionManager);
        this.razorpayKeyId = razorpayKeyId;
    }

    /** Deliberately NOT @Transactional: the Razorpay HTTP call must not run inside a DB transaction. */
    public CheckoutInitiateResponse initiate(Long buyerId, FulfilmentMode mode, String deliveryAddress) {
        PendingPayment pending = Objects.requireNonNull(
                tx.execute(status -> createPendingPayment(buyerId, mode, deliveryAddress)));

        RazorpayOrderResponse order;
        try {
            order = razorpay.createOrder(
                    pending.amountPaise(),
                    "pay_" + pending.paymentId(),
                    Map.of("internal_payment_id", String.valueOf(pending.paymentId())));
        } catch (RuntimeException e) {
            markFailed(pending.paymentId(), e);
            throw e;
        }

        tx.executeWithoutResult(status -> {
            Payment payment = payments.findById(pending.paymentId())
                    .orElseThrow(() -> new IllegalStateException("Payment vanished: " + pending.paymentId()));
            payment.attachRazorpayOrderId(order.id());
        });

        log.info("Checkout initiated: payment {}, razorpay order {}", pending.paymentId(), order.id());
        return new CheckoutInitiateResponse(
                pending.paymentId(), order.id(), razorpayKeyId, pending.amountPaise(), CURRENCY);
    }

    // ---------------------------------------------------------------
    // Transaction 1: validate the cart and persist the immutable snapshot
    // ---------------------------------------------------------------

    private PendingPayment createPendingPayment(Long buyerId, FulfilmentMode mode, String deliveryAddress) {
        Cart cart = carts.findByUserIdForUpdate(buyerId)
                .orElseThrow(() -> new BadRequestException("Your cart is empty."));
        List<CartItem> rows = cartItems.findByCartIdOrderByIdAsc(cart.getId());
        if (rows.isEmpty()) {
            throw new BadRequestException("Your cart is empty.");
        }

        String addressSnapshot = resolveAddress(mode, deliveryAddress);

        Map<Long, Product> byId = products
                .findAllById(rows.stream().map(CartItem::getProductId).toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Long sellerId = null;
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem row : rows) {
            Product p = byId.get(row.getProductId());
            if (p == null) {
                throw new ConflictException("A product in your cart no longer exists. Remove it and try again.");
            }
            if (!p.isActive()) {
                throw new ConflictException(p.getName() + " is no longer available. Remove it from your cart.");
            }
            if (sellerId == null) {
                sellerId = p.getSellerProfileId();
            } else if (!sellerId.equals(p.getSellerProfileId())) {
                throw new ConflictException("Your cart has items from more than one seller. Clear your cart and start again.");
            }
            if (!supports(p, mode)) {
                throw new BadRequestException(p.getName() + " does not support " + label(mode) + ".");
            }
            if (p.getFulfilmentType() == FulfilmentType.READY_STOCK) {
                Integer inventory = p.getInventoryQty();
                if (inventory == null || inventory < row.getQuantity()) {
                    throw new ConflictException("Not enough stock for " + p.getName()
                            + ". Available: " + (inventory == null ? 0 : inventory) + ".");
                }
            }
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(row.getQuantity())));
        }
        if (total.signum() <= 0) {
            throw new BadRequestException("Order total must be greater than zero.");
        }

        Payment payment = payments.save(new Payment(buyerId, sellerId, total, mode, addressSnapshot));
        for (CartItem row : rows) {
            Product p = byId.get(row.getProductId());
            paymentItems.save(new PaymentItem(
                    payment.getPaymentId(), p.getId(), p.getName(), p.getPrice(), row.getQuantity()));
        }
        return new PendingPayment(payment.getPaymentId(), PaiseConverter.toPaise(total));
    }

    private String resolveAddress(FulfilmentMode mode, String deliveryAddress) {
        if (mode == FulfilmentMode.PICKUP) {
            return null;
        }
        String trimmed = deliveryAddress == null ? "" : deliveryAddress.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("A delivery address is required for " + label(mode) + ".");
        }
        return trimmed;
    }

    private static boolean supports(Product p, FulfilmentMode mode) {
        return switch (mode) {
            case SELF_DELIVERY -> p.isSupportsSelfDelivery();
            case PICKUP -> p.isSupportsPickup();
            case POSTAL -> p.isSupportsPostal();
        };
    }

    private static String label(FulfilmentMode mode) {
        return switch (mode) {
            case SELF_DELIVERY -> "self delivery";
            case PICKUP -> "pickup";
            case POSTAL -> "postal delivery";
        };
    }

    // ---------------------------------------------------------------
    // Failure path: Razorpay did not create an order
    // ---------------------------------------------------------------

    private void markFailed(Long paymentId, RuntimeException original) {
        try {
            tx.executeWithoutResult(status -> payments.findById(paymentId).ifPresent(p -> {
                if (p.getStatus() == PaymentStatus.PENDING) {
                    p.markFailed();
                }
            }));
        } catch (RuntimeException ex) {
            original.addSuppressed(ex);
            log.error("Could not mark payment {} FAILED", paymentId, ex);
        }
    }

    private record PendingPayment(Long paymentId, long amountPaise) {
    }
}