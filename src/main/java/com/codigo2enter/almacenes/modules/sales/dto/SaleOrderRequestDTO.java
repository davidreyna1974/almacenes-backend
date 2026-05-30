package com.codigo2enter.almacenes.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderRequestDTO {

    @NotNull(message = "El ID del cliente es obligatorio")
    private Long clientId;

    private String notes;

    @NotNull(message = "La lista de detalles es obligatoria")
    @Size(min = 1, message = "La orden debe tener al menos un detalle")
    @Valid
    private List<SaleOrderDetailRequestDTO> details;
}
