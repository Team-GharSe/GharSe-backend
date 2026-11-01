package com.gharse.catalog;

import com.gharse.review.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductPublicControllerTest {

    private final ProductQueryService service = mock(ProductQueryService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProductPublicController(service, mock(ReviewService.class))).build();

    @Test
    void etaDefaultsToQuantityOne() throws Exception {
        when(service.eta(5L, 1)).thenReturn(Map.of("estimatedReadyDate", "2026-11-05"));

        mvc.perform(get("/v1/products/5/eta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedReadyDate").value("2026-11-05"));

        verify(service).eta(5L, 1);
    }

    @Test
    void etaPassesTheRequestedQuantity() throws Exception {
        when(service.eta(5L, 4)).thenReturn(Map.of("estimatedReadyDate", "2026-11-07"));

        mvc.perform(get("/v1/products/5/eta").param("quantity", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedReadyDate").value("2026-11-07"));

        verify(service).eta(5L, 4);
    }

    @Test
    void nonNumericQuantityIs400() throws Exception {
        mvc.perform(get("/v1/products/5/eta").param("quantity", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void nonNumericProductIdIs400() throws Exception {
        mvc.perform(get("/v1/products/abc/eta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}