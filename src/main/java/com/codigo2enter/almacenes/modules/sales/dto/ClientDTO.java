package com.codigo2enter.almacenes.modules.sales.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO unificado para clientes — funciona tanto como request (POST/PUT) como
 * response (GET). Los campos de auditoría (id, createdAt, createdById, etc.)
 * son ignorados por el mapper en toEntity() y solo se populan en la respuesta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    private Long id;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String name;

    @Size(max = 13, message = "El RFC no puede superar 13 caracteres")
    private String rfc;

    @Size(max = 100, message = "El nombre de contacto no puede superar 100 caracteres")
    private String contactName;

    @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
    private String phone;

    @Email(message = "El email no tiene un formato válido")
    private String email;

    private String address;

    private boolean active;

    // Campos de auditoría — solo salida
    private LocalDateTime createdAt;
    private Long createdById;
    private String createdByUsername;
    private LocalDateTime updatedAt;
    private Long updatedById;
    private String updatedByUsername;
}
