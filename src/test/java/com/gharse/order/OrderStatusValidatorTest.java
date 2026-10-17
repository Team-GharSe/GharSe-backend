package com.gharse.order;

import com.gharse.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class OrderStatusValidatorTest {
    static Stream<Arguments> allTransitions() {
        return Stream.of(OrderStatus.values()).flatMap(from ->
            Stream.of(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    void validatesEveryPair(OrderStatus from, OrderStatus to) {
        // Independent specification: four allowed transitions; other 21 rejected.
        boolean allowed = (from == OrderStatus.CONFIRMED && to == OrderStatus.IN_PRODUCTION)
            || (from == OrderStatus.IN_PRODUCTION && to == OrderStatus.READY)
            || (from == OrderStatus.READY && to == OrderStatus.OUT_FOR_DELIVERY)
            || (from == OrderStatus.OUT_FOR_DELIVERY && to == OrderStatus.DELIVERED);
        if (allowed) assertDoesNotThrow(() -> OrderStatusValidator.validate(from, to));
        else assertThrows(ConflictException.class, () -> OrderStatusValidator.validate(from, to));
    }

    @Test
    void rejectsNullStates() {
        assertThrows(ConflictException.class, () -> OrderStatusValidator.validate(null, OrderStatus.CONFIRMED));
        assertThrows(ConflictException.class, () -> OrderStatusValidator.validate(OrderStatus.READY, null));
        assertThrows(ConflictException.class, () -> OrderStatusValidator.validate(null, null));
    }

    @Test
    void entityEnforcesTheGuardAndRemainsDelivered() {
        Order order = new Order(1L, 20L, 70L, FulfilmentMode.PICKUP, null);
        assertThrows(ConflictException.class, () -> order.transitionTo(OrderStatus.READY));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        order.transitionTo(OrderStatus.IN_PRODUCTION);
        order.transitionTo(OrderStatus.READY);
        order.transitionTo(OrderStatus.OUT_FOR_DELIVERY);
        order.transitionTo(OrderStatus.DELIVERED);
        for (OrderStatus requested : OrderStatus.values()) {
            assertThrows(ConflictException.class, () -> order.transitionTo(requested));
            assertEquals(OrderStatus.DELIVERED, order.getStatus());
        }
    }
}
