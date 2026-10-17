package com.gharse.order;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.auth.JwtUtil;
import com.gharse.common.exception.GlobalExceptionHandler;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.config.SecurityConfig;
import com.gharse.order.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual uploaded SecurityConfig/JWT filter + MVC, mocked service; no MySQL needed. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OrderHttpTest.Config.class)
class OrderHttpTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, OrderController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean OrderService orderService() { return mock(OrderService.class); }
        @Bean JwtUtil jwtUtil() {
            // Ephemeral test key. Never copies or commits an application secret.
            return new JwtUtil(UUID.randomUUID().toString() + UUID.randomUUID());
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired OrderService service;
    @Autowired JwtUtil jwt;
    private MockMvc mvc;
    private String buyerToken;
    private String sellerToken;
    private final AuthenticatedUser buyer = new AuthenticatedUser(20L, "BUYER");
    private final AuthenticatedUser seller = new AuthenticatedUser(30L, "SELLER");

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        buyerToken = "Bearer " + jwt.generate(20L, "BUYER");
        sellerToken = "Bearer " + jwt.generate(30L, "SELLER");
    }

    @Test
    void noTokenIs401() throws Exception {
        mvc.perform(get("/v1/orders/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    @Test
    void badTokenIs401() throws Exception {
        mvc.perform(get("/v1/orders/me").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void validBuyerTokenInjectsActualAuthenticatedUser() throws Exception {
        when(service.myOrders(buyer)).thenReturn(List.of());
        mvc.perform(get("/v1/orders/me").header("Authorization", buyerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.data").isArray());
        verify(service).myOrders(buyer);
    }

    @Test
    void callerSuppliedIdentityIsNotUsed() throws Exception {
        when(service.myOrders(buyer)).thenReturn(List.of());
        mvc.perform(get("/v1/orders/me?buyerId=999&role=ADMIN").header("Authorization", buyerToken))
            .andExpect(status().isOk());
        verify(service).myOrders(buyer);
    }

    @Test
    void sellerCanListOwnPurchases() throws Exception {
        when(service.myOrders(seller)).thenReturn(List.of());
        mvc.perform(get("/v1/orders/me").header("Authorization", sellerToken))
            .andExpect(status().isOk());
        verify(service).myOrders(seller);
    }

    @Test
    void adminHasNoOrderAccessOverride() throws Exception {
        mvc.perform(get("/v1/orders/10").header("Authorization", "Bearer " + jwt.generate(1L, "ADMIN")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(service);
    }

    @Test
    void buyerCannotListSellerOrders() throws Exception {
        mvc.perform(get("/v1/seller/me/orders").header("Authorization", buyerToken))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void sellerFilterIsPassedAsEnum() throws Exception {
        when(service.sellerOrders(seller, OrderStatus.READY)).thenReturn(List.of());
        mvc.perform(get("/v1/seller/me/orders?status=READY").header("Authorization", sellerToken))
            .andExpect(status().isOk());
        verify(service).sellerOrders(seller, OrderStatus.READY);
    }

    @Test
    void absentSellerFilterIsNull() throws Exception {
        when(service.sellerOrders(seller, null)).thenReturn(List.of());
        mvc.perform(get("/v1/seller/me/orders").header("Authorization", sellerToken))
            .andExpect(status().isOk());
        verify(service).sellerOrders(seller, null);
    }

    @Test
    void invalidStatusFilterIs400Not500() throws Exception {
        mvc.perform(get("/v1/seller/me/orders?status=INVALID").header("Authorization", sellerToken))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void invalidOrderIdIs400() throws Exception {
        mvc.perform(get("/v1/orders/not-a-number").header("Authorization", buyerToken))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void buyerCannotPatchStatus() throws Exception {
        mvc.perform(patch("/v1/orders/10/status").header("Authorization", buyerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PRODUCTION\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void validPatchCallsServiceWithVerifiedSeller() throws Exception {
        OrderResponse response = new OrderResponse(10L, 1L, 20L, 70L, OrderStatus.IN_PRODUCTION,
            FulfilmentMode.PICKUP, null, null, null, List.of());
        when(service.updateStatus(seller, 10L, OrderStatus.IN_PRODUCTION)).thenReturn(response);
        mvc.perform(patch("/v1/orders/10/status").header("Authorization", sellerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PRODUCTION\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PRODUCTION"));
        verify(service).updateStatus(seller, 10L, OrderStatus.IN_PRODUCTION);
    }

    @Test
    void missingAndUnknownBodyStatusesAre400() throws Exception {
        for (String body : List.of("{}", "{\"status\":null}", "{\"status\":\"INVALID\"}", "{")) {
            mvc.perform(patch("/v1/orders/10/status").header("Authorization", sellerToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void inaccessibleOrderKeepsExisting404Envelope() throws Exception {
        when(service.getOrder(buyer, 10L)).thenThrow(new ResourceNotFoundException("Order not found."));
        mvc.perform(get("/v1/orders/10").header("Authorization", buyerToken))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(404));
    }
}
