package com.codigo2enter.almacenes.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO de salida para exponer la informacion publica de un usuario.
 * Protege el sistema omitiendo el hash de la contraseña en las respuestas HTTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private Long id;
    private String username;
    private String email;
    private Boolean active;
    private LocalDateTime createdAt;
    private Set<String> roles; // Simplifica el objeto Role enviando solo el texto del rol (ej. "ROLE_ADMIN")
}
