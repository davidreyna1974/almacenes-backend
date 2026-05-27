package com.codigo2enter.almacenes.modules.purchases.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de salida que representa una línea de detalle de una orden de compra.
 *
 * Aplana las relaciones @ManyToOne en campos simples:
 *   product → productId + productSku + productName
 *
 * Esto permite a Angular mostrar la información del producto en tablas
 * y formularios sin necesidad de peticiones adicionales para resolver
 * la relación Product.
 *
 * La relación purchaseOrder no se incluye en el DTO de detalle porque
 * los detalles siempre se devuelven anidados dentro de PurchaseOrderResponseDTO
 * — incluir el id de la orden en cada detalle sería información redundante.
 *
 * No lleva anotaciones de validación — es exclusivamente de salida.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDetailResponseDTO {

    private Long id;

    /** Número de unidades de este detalle. */
    private int quantity;

    /** Precio unitario pactado al momento de crear o editar el detalle.
     *  Puede diferir de Product.price si el precio del catálogo cambió después. */
    private BigDecimal unitPrice;

    /** Resultado de quantity × unitPrice, calculado por el servicio. */
    private BigDecimal subtotal;

    /** Relación Product aplanada en tres campos para facilitar el consumo
     *  desde Angular sin peticiones adicionales al servidor. */
    private Long productId;
    private String productSku;
    private String productName;
}
