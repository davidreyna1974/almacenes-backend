package com.codigo2enter.almacenes.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderUpdateRequestDTO {

    @NotNull(message = "El ID del cliente es obligatorio")
    private Long clientId;

    private String notes;
}
