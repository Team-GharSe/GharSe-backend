package com.gharse.catalog;

import com.gharse.catalog.dto.BacklogItem;
import com.gharse.common.exception.BadRequestException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderStatus;
import com.gharse.seller.SellerProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductQueryServiceEtaTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final EtaCalculator etaCalculator = mock(EtaCalculator.class);
    private final ProductQueryService service = new ProductQueryService(
            products, mock(ProductImageRepository.class), mock(CategoryRepository.class),
            mock(SellerProfileRepository.class), orderItems, etaCalculator);

    private static Product product(long id, String name, FulfilmentType type, Integer inventory, Integer minutes) {
        Product p = new Product();
        p.setId(id);
        p.setSellerProfileId(7L);
        p.setName(name);
        p.setFulfilmentType(type);
        p.setInventoryQty(inventory);
        p.setProductionTimeMinutesPerUnit(minutes);
        p.setActive(true);
        return p;
    }

    @Test
    void readyStockEtaIsUnchangedAndSkipsTheCalculator() {
        when(products.findByIdAndActiveTrue(5L))
                .thenReturn(Optional.of(product(5, "Cake", FulfilmentType.READY_STOCK, 4, null)));

        Map<String, Object> eta = service.eta(5L, 3);

        assertEquals(true, eta.get("inStock"));
        assertEquals(4, eta.get("quantityRemaining"));
        verifyNoInteractions(etaCalculator);
    }

    @Test
    void madeToOrderUsesTheCalculatorWithTheRequestedQuantity() {
        Product cake = product(5, "Custom Cake", FulfilmentType.MADE_TO_ORDER, null, 120);
        when(products.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(cake));
        when(etaCalculator.estimatedReadyDate(cake, 3)).thenReturn(LocalDate.of(2026, 11, 5));

        assertEquals(Map.of("estimatedReadyDate", "2026-11-05"), service.eta(5L, 3));
    }

    @Test
    void quantityOutsideOneToThousandIsBadRequest() {
        assertThrows(BadRequestException.class, () -> service.eta(5L, 0));
        assertThrows(BadRequestException.class, () -> service.eta(5L, -1));
        assertThrows(BadRequestException.class, () -> service.eta(5L, 1001));
        verifyNoInteractions(products, etaCalculator);

        Product cake = product(5, "Custom Cake", FulfilmentType.MADE_TO_ORDER, null, 120);
        when(products.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(cake));
        when(etaCalculator.estimatedReadyDate(cake, 1000)).thenReturn(LocalDate.of(2026, 12, 1));
        assertDoesNotThrow(() -> service.eta(5L, 1000));
    }

    @Test
    void inactiveOrMissingProductIs404() {
        when(products.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eta(99L, 1));
    }

    @Test
    void backlogRowsShareTheSellersWholeQueueDate() {
        Product cake = product(5, "Custom Cake", FulfilmentType.MADE_TO_ORDER, null, 120);
        Product brownies = product(6, "Brownies Box", FulfilmentType.MADE_TO_ORDER, null, 60);
        List<OrderStatus> open = List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION);
        when(products.findBySellerProfileIdAndActiveTrueAndFulfilmentType(7L, FulfilmentType.MADE_TO_ORDER))
                .thenReturn(List.of(cake, brownies));
        when(orderItems.sumBacklogUnits(5L, open)).thenReturn(10L);
        when(orderItems.sumBacklogUnits(6L, open)).thenReturn(20L);
        when(etaCalculator.queueClearDate(7L)).thenReturn(LocalDate.of(2026, 11, 3));

        List<BacklogItem> rows = service.sellerBacklog(7L);

        assertEquals(2, rows.size());
        assertEquals(new BacklogItem(5L, "Custom Cake", 10L, 120, "2026-11-03"), rows.get(0));
        assertEquals(new BacklogItem(6L, "Brownies Box", 20L, 60, "2026-11-03"), rows.get(1));
        verify(etaCalculator, times(1)).queueClearDate(7L);
    }

    @Test
    void backlogWithNoListingsIsEmptyAndSkipsTheCalculator() {
        when(products.findBySellerProfileIdAndActiveTrueAndFulfilmentType(7L, FulfilmentType.MADE_TO_ORDER))
                .thenReturn(List.of());

        assertTrue(service.sellerBacklog(7L).isEmpty());
        verifyNoInteractions(etaCalculator);
    }
}