package com.codigo2enter.almacenes.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Cliente con sus órdenes APPROVED (reservas activas). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedClientDTO {
    private Long clientId;
    private String clientName;
    private int totalReservedOrders;
    private BigDecimal totalReservedValue;
    private List<ReservedClientOrderDTO> orders;
}
