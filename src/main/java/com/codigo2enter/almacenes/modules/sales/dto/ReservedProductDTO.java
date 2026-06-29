package com.codigo2enter.almacenes.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Producto con desglose de sus reservas activas por orden APPROVED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedProductDTO {
    private Long productId;
    private String productSku;
    private String productName;
    private int totalReservedQty;
    private BigDecimal unitPrice;
    private BigDecimal totalReservedValue;
    private List<ReservedProductOrderDTO> orders;
}
