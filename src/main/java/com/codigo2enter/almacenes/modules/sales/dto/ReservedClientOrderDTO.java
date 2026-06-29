package com.codigo2enter.almacenes.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Orden APPROVED dentro del contexto de un cliente con reservas. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedClientOrderDTO {
    private Long orderId;
    private String orderNumber;
    private BigDecimal totalAmount;
    private LocalDateTime approvedAt;
    private int totalItems;
}
