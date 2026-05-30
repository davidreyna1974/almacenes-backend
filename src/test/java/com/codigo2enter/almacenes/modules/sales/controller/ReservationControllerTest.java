package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ReservationService reservationService;
    @MockBean JwtUtils jwtUtils;

    private static final String BASE = "/api/v1/sales/reservations";

    @Test
    void getSummary_retorna200() throws Exception {
        ReservationSummaryDTO dto = ReservationSummaryDTO.builder()
                .totalProductsWithReservations(2).totalReservedUnits(10)
                .totalReservedValue(new BigDecimal("15000.00")).totalApprovedOrders(3).build();
        when(reservationService.getSummary()).thenReturn(dto);

        mockMvc.perform(get(BASE + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApprovedOrders").value(3))
                .andExpect(jsonPath("$.totalReservedUnits").value(10));
    }

    @Test
    void getReservedProducts_retorna200() throws Exception {
        ReservedProductDTO dto = ReservedProductDTO.builder()
                .productId(1L).productSku("TAL-001").totalReservedQty(5).orders(List.of()).build();
        when(reservationService.getReservedProducts()).thenReturn(List.of(dto));

        mockMvc.perform(get(BASE + "/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productSku").value("TAL-001"));
    }

    @Test
    void getProductReservationDetail_retorna200() throws Exception {
        ReservedProductDTO dto = ReservedProductDTO.builder()
                .productId(1L).productSku("TAL-001").totalReservedQty(5).orders(List.of()).build();
        when(reservationService.getProductReservationDetail(1L)).thenReturn(dto);

        mockMvc.perform(get(BASE + "/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    void getClientsWithReservations_retorna200() throws Exception {
        ReservedClientDTO dto = ReservedClientDTO.builder()
                .clientId(1L).clientName("Comercial Reyes SA")
                .totalReservedOrders(1).totalReservedValue(new BigDecimal("7500.00"))
                .orders(List.of()).build();
        when(reservationService.getClientsWithReservations()).thenReturn(List.of(dto));

        mockMvc.perform(get(BASE + "/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientName").value("Comercial Reyes SA"));
    }

    @Test
    void getClientReservationDetail_retorna200() throws Exception {
        ReservedClientDTO dto = ReservedClientDTO.builder()
                .clientId(1L).clientName("Comercial Reyes SA")
                .totalReservedOrders(1).totalReservedValue(new BigDecimal("7500.00"))
                .orders(List.of()).build();
        when(reservationService.getClientReservationDetail(1L)).thenReturn(dto);

        mockMvc.perform(get(BASE + "/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(1));
    }
}
