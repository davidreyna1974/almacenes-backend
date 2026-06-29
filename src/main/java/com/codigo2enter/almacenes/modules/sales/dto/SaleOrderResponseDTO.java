package com.codigo2enter.almacenes.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderResponseDTO {

    private Long id;
    private String orderNumber;
    private String status;
    private String notes;
    private BigDecimal totalAmount;

    private Long clientId;
    private String clientName;

    private LocalDateTime createdAt;
    private Long createdById;
    private String createdByUsername;

    private LocalDateTime updatedAt;
    private Long updatedById;
    private String updatedByUsername;

    private LocalDateTime approvedAt;
    private Long approvedById;
    private String approvedByUsername;

    private LocalDateTime deliveredAt;
    private Long deliveredById;
    private String deliveredByUsername;

    private LocalDateTime cancelledAt;
    private Long cancelledById;
    private String cancelledByUsername;

    private List<SaleOrderDetailResponseDTO> details;
}
