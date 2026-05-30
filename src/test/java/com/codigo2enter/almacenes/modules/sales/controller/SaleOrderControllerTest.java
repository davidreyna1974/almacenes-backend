package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.service.SaleOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SaleOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class SaleOrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SaleOrderService saleOrderService;
    @MockBean JwtUtils jwtUtils;

    private static final String BASE = "/api/v1/sales/orders";

    private SaleOrderResponseDTO buildResponse() {
        return SaleOrderResponseDTO.builder()
                .id(1L).orderNumber("OV-2026-0001").status("PENDING")
                .totalAmount(new BigDecimal("7500.00")).build();
    }

    @Test
    void createOrder_bodyValido_retorna201() throws Exception {
        SaleOrderDetailRequestDTO detailDto = SaleOrderDetailRequestDTO.builder()
                .productId(1L).quantity(5).unitPrice(new BigDecimal("1500.00")).build();
        SaleOrderRequestDTO dto = SaleOrderRequestDTO.builder()
                .clientId(1L).details(List.of(detailDto)).build();
        when(saleOrderService.createOrder(any())).thenReturn(buildResponse());

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("OV-2026-0001"));
    }

    @Test
    void createOrder_sinClienteId_retorna400() throws Exception {
        SaleOrderRequestDTO dto = SaleOrderRequestDTO.builder()
                .details(List.of(SaleOrderDetailRequestDTO.builder()
                        .productId(1L).quantity(1).unitPrice(BigDecimal.ONE).build()))
                .build();

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_sinDetalles_retorna400() throws Exception {
        SaleOrderRequestDTO dto = SaleOrderRequestDTO.builder()
                .clientId(1L).details(List.of()).build();

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findById_retorna200() throws Exception {
        when(saleOrderService.findById(1L)).thenReturn(buildResponse());

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findByStatus_retorna200() throws Exception {
        when(saleOrderService.findByStatus("PENDING")).thenReturn(List.of(buildResponse()));

        mockMvc.perform(get(BASE + "/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void findByClientId_retorna200() throws Exception {
        when(saleOrderService.findByClientId(1L)).thenReturn(List.of(buildResponse()));

        mockMvc.perform(get(BASE + "/client/1"))
                .andExpect(status().isOk());
    }

    @Test
    void approveOrder_retorna200() throws Exception {
        SaleOrderResponseDTO approved = buildResponse();
        approved.setStatus("APPROVED");
        when(saleOrderService.approveOrder(1L)).thenReturn(approved);

        mockMvc.perform(patch(BASE + "/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void deliverOrder_retorna200() throws Exception {
        SaleOrderResponseDTO delivered = buildResponse();
        delivered.setStatus("DELIVERED");
        when(saleOrderService.deliverOrder(1L)).thenReturn(delivered);

        mockMvc.perform(patch(BASE + "/1/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void cancelOrder_retorna200() throws Exception {
        SaleOrderResponseDTO cancelled = buildResponse();
        cancelled.setStatus("CANCELLED");
        when(saleOrderService.cancelOrder(1L)).thenReturn(cancelled);

        mockMvc.perform(patch(BASE + "/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void addDetail_bodyValido_retorna201() throws Exception {
        SaleOrderDetailRequestDTO dto = SaleOrderDetailRequestDTO.builder()
                .productId(1L).quantity(3).unitPrice(new BigDecimal("500.00")).build();
        when(saleOrderService.addDetail(eq(1L), any())).thenReturn(buildResponse());

        mockMvc.perform(post(BASE + "/1/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    void updateDetail_retorna200() throws Exception {
        SaleOrderDetailUpdateRequestDTO dto = SaleOrderDetailUpdateRequestDTO.builder()
                .quantity(10).unitPrice(new BigDecimal("1500.00")).build();
        when(saleOrderService.updateDetail(eq(1L), eq(1L), any())).thenReturn(buildResponse());

        mockMvc.perform(put(BASE + "/1/details/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void removeDetail_retorna204() throws Exception {
        mockMvc.perform(delete(BASE + "/1/details/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateOrder_retorna200() throws Exception {
        SaleOrderUpdateRequestDTO dto = SaleOrderUpdateRequestDTO.builder()
                .clientId(1L).notes("Nueva nota").build();
        when(saleOrderService.updateOrder(eq(1L), any())).thenReturn(buildResponse());

        mockMvc.perform(put(BASE + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void findByProductId_retorna200() throws Exception {
        when(saleOrderService.findByProductId(1L)).thenReturn(List.of(buildResponse()));

        mockMvc.perform(get(BASE + "/product/1"))
                .andExpect(status().isOk());
    }
}
