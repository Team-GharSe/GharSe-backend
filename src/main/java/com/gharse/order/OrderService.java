package com.gharse.order;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.common.exception.UnauthorizedException;
import com.gharse.order.dto.OrderResponse;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final SellerProfileRepository sellers;

    public OrderService(OrderRepository orders, OrderItemRepository items,
                        SellerProfileRepository sellers) {
        this.orders = orders;
        this.items = items;
        this.sellers = sellers;
    }

    public List<OrderResponse> myOrders(AuthenticatedUser user) {
        requireParticipant(user);
        // An onboarded seller may still read orders they previously bought.
        return orders.findByBuyerIdOrderByCreatedAtDescIdDesc(user.id())
            .stream().map(this::response).toList();
    }

    public OrderResponse getOrder(AuthenticatedUser user, Long orderId) {
        requireParticipant(user);
        Order order = orders.findById(orderId).orElseThrow(OrderService::notFound);
        boolean buyer = Objects.equals(order.getBuyerId(), user.id());
        boolean owningSeller = !buyer && "SELLER".equals(user.role())
            && sellers.findById(order.getSellerProfileId())
                .map(profile -> Objects.equals(profile.getUserId(), user.id()))
                .orElse(false);
        if (!buyer && !owningSeller) throw notFound();
        return response(order);
    }

    public List<OrderResponse> sellerOrders(AuthenticatedUser user, OrderStatus status) {
        SellerProfile seller = currentSeller(user);
        List<Order> result = status == null
            ? orders.findBySellerProfileIdOrderByCreatedAtDescIdDesc(seller.getId())
            : orders.findBySellerProfileIdAndStatusOrderByCreatedAtDescIdDesc(seller.getId(), status);
        return result.stream().map(this::response).toList();
    }

    @Transactional
    public OrderResponse updateStatus(AuthenticatedUser user, Long orderId, OrderStatus requested) {
        SellerProfile seller = currentSeller(user);
        // Scope by seller AND lock before reading status. A concurrent PATCH then
        // validates against the committed new state instead of a stale copy.
        Order order = orders.findOwnedForUpdate(orderId, seller.getId())
            .orElseThrow(OrderService::notFound);
        order.transitionTo(requested);
        Order saved = orders.saveAndFlush(order);
        return response(saved);
    }

    private SellerProfile currentSeller(AuthenticatedUser user) {
        requireIdentity(user);
        if (!"SELLER".equals(user.role())) throw new AccessDeniedException("Seller role required.");
        return sellers.findByUserId(user.id())
            .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found."));
    }

    private static void requireParticipant(AuthenticatedUser user) {
        requireIdentity(user);
        if (!"BUYER".equals(user.role()) && !"SELLER".equals(user.role())) {
            throw new AccessDeniedException("Buyer or seller role required.");
        }
    }

    private static void requireIdentity(AuthenticatedUser user) {
        if (user == null || user.id() == null || user.id() <= 0) {
            throw new UnauthorizedException("Please log in to continue.");
        }
    }

    private static ResourceNotFoundException notFound() {
        // Same response for absent and nonowned orders, matching catalog ownership policy.
        return new ResourceNotFoundException("Order not found.");
    }

    private OrderResponse response(Order order) {
        return OrderResponse.from(order, items.findByOrderIdOrderByIdAsc(order.getId()));
    }
}
