package com.codigo2enter.almacenes.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Orden dentro del contexto de un producto con reservas activas. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedProductOrderDTO {
    private Long orderId;
    private String orderNumber;
    private int quantity;
    private BigDecimal subtotal;
    private Long clientId;
    private String clientName;
    private LocalDateTime approvedAt;
}
