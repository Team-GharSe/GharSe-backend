package com.gharse.payment;

import com.gharse.cart.Cart;
import com.gharse.cart.CartItemRepository;
import com.gharse.cart.CartRepository;
import com.gharse.catalog.FulfilmentType;
import com.gharse.catalog.Product;
import com.gharse.catalog.ProductRepository;
import com.gharse.order.Order;
import com.gharse.order.OrderItem;
import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderRepository;
import com.gharse.order.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Processes verified Razorpay webhook events. This is the only code path that books an Order
 * (FR15, NFR4). Every method is safe to run twice for the same event (NFR7).
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    static final int MAX_ATTEMPTS = 3;
    private static final String CURRENCY = "INR";

    private final PaymentRepository payments;
    private final PaymentItemRepository paymentItems;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ProductRepository products;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

    public PaymentWebhookService(PaymentRepository payments,
                                 PaymentItemRepository paymentItems,
                                 OrderRepository orders,
                                 OrderItemRepository orderItems,
                                 ProductRepository products,
                                 CartRepository carts,
                                 CartItemRepository cartItems,
                                 PlatformTransactionManager transactionManager,
                                 ApplicationEventPublisher events) {
        this.payments = payments;
        this.paymentItems = paymentItems;
        this.orders = orders;
        this.orderItems = orderItems;
        this.products = products;
        this.carts = carts;
        this.cartItems = cartItems;
        this.tx = new TransactionTemplate(transactionManager);
        this.events = events;
    }

    /**
     * Books the order for a captured payment. Retries the whole transaction a bounded number of times
     * when a concurrent stock update wins the @Version check. If every attempt fails, the exception
     * propagates so the webhook answers 5xx and Razorpay retries later; that is safe because this
     * method is idempotent.
     */
    public void handleCaptured(RazorpayWebhookEvent.PaymentEntity entity) {
        if (entity == null || entity.orderId() == null || entity.orderId().isBlank()) {
            log.warn("payment.captured without a razorpay order id; ignored");
            return;
        }
        for (int attempt = 1; ; attempt++) {
            try {
                tx.executeWithoutResult(status -> bookOrder(entity));
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.error("Giving up on razorpay order {} after {} stock conflicts", entity.orderId(), attempt);
                    throw e;
                }
                log.warn("Stock version conflict for razorpay order {} (attempt {}); retrying", entity.orderId(), attempt);
            }
        }
    }

    /** A failed attempt only changes a PENDING payment. A late failed event never overwrites SUCCESS. */
    public void handleFailed(RazorpayWebhookEvent.PaymentEntity entity) {
        if (entity == null || entity.orderId() == null || entity.orderId().isBlank()) {
            log.warn("payment.failed without a razorpay order id; ignored");
            return;
        }
        tx.executeWithoutResult(status -> {
            Optional<Payment> found = payments.findByRazorpayOrderIdForUpdate(entity.orderId());
            if (found.isEmpty()) {
                log.warn("payment.failed for unknown razorpay order {}; ignored", entity.orderId());
                return;
            }
            Payment payment = found.get();
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.markFailed();
                log.info("Payment {} marked FAILED ({})", payment.getPaymentId(), entity.errorCode());
            } else {
                log.info("payment.failed ignored: payment {} is already {}", payment.getPaymentId(), payment.getStatus());
            }
        });
    }

    // ---------------------------------------------------------------
    // One attempt, one transaction
    // ---------------------------------------------------------------

    private void bookOrder(RazorpayWebhookEvent.PaymentEntity entity) {
        Optional<Payment> found = payments.findByRazorpayOrderIdForUpdate(entity.orderId());
        if (found.isEmpty()) {
            log.warn("payment.captured for unknown razorpay order {}; ignored", entity.orderId());
            return;
        }
        Payment payment = found.get();
        Long paymentId = payment.getPaymentId();

        if (payment.getStatus() == PaymentStatus.SUCCESS || orders.findByPaymentId(paymentId).isPresent()) {
            log.info("Duplicate payment.captured for payment {}; nothing to do", paymentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.warn("payment.captured for REFUNDED payment {}; ignored", paymentId);
            return;
        }

        long expectedPaise = PaiseConverter.toPaise(payment.getAmountTotal());
        if (!Long.valueOf(expectedPaise).equals(entity.amount()) || !CURRENCY.equals(entity.currency())) {
            log.error("Webhook amount/currency mismatch for payment {}: expected {} {} but got {} {}; NOT booking",
                    paymentId, expectedPaise, CURRENCY, entity.amount(), entity.currency());
            return;
        }

        List<PaymentItem> snapshot = paymentItems.findByPaymentIdOrderByPaymentItemId(paymentId);
        if (snapshot.isEmpty()) {
            log.error("Payment {} has no snapshot items; NOT booking", paymentId);
            return;
        }

        for (PaymentItem item : snapshot) {
            decrementStock(item);
        }

        Order order = orders.save(new Order(paymentId, payment.getBuyerId(), payment.getSellerProfileId(),
                payment.getFulfilmentModeSelected(), payment.getDeliveryAddressSnapshot()));
        for (PaymentItem item : snapshot) {
            orderItems.save(new OrderItem(order.getId(), item.getProductId(),
                    item.getProductNameSnapshot(), item.getPriceSnapshot(), item.getQuantity()));
        }
        payment.markSuccess(LocalDateTime.now());
        clearCart(payment.getBuyerId(), snapshot);
        events.publishEvent(OrderStatusChangedEvent.of(order, null));

        log.info("Order {} booked for payment {}", order.getId(), paymentId);
    }

    /**
     * READY_STOCK only. The flush triggers the @Version check right here, so a lost race fails this
     * attempt before an order is created. If stock is still insufficient after a fresh read, the
     * order is booked anyway and stock is clamped at 0 (decision: no refund path in this phase).
     */
    private void decrementStock(PaymentItem item) {
        Product product = products.findById(item.getProductId())
                .orElseThrow(() -> new IllegalStateException("Product missing for payment item: " + item.getProductId()));
        if (product.getFulfilmentType() != FulfilmentType.READY_STOCK) {
            return;
        }
        int available = product.getInventoryQty() == null ? 0 : product.getInventoryQty();
        int remaining = available - item.getQuantity();
        if (remaining < 0) {
            log.warn("Oversold product {}: had {}, bought {}; booking anyway, stock set to 0",
                    product.getId(), available, item.getQuantity());
            remaining = 0;
        }
        product.setInventoryQty(remaining);
        products.saveAndFlush(product);
    }

    /** Removes only what this checkout bought; anything the buyer added since stays in the cart. */
    private void clearCart(Long buyerId, List<PaymentItem> snapshot) {
        Optional<Cart> found = carts.findByUserIdForUpdate(buyerId);
        if (found.isEmpty()) {
            return;
        }
        Cart cart = found.get();
        for (PaymentItem item : snapshot) {
            cartItems.findByCartIdAndProductId(cart.getId(), item.getProductId()).ifPresent(line -> {
                if (line.getQuantity() <= item.getQuantity()) {
                    cartItems.delete(line);
                } else {
                    line.changeQuantity(line.getQuantity() - item.getQuantity());
                }
            });
        }
        if (cartItems.countByCartId(cart.getId()) == 0) {
            cart.releaseLock();
        }
    }
}