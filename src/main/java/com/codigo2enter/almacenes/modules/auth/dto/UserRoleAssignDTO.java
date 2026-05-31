package com.codigo2enter.almacenes.modules.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO para reemplazar la lista completa de roles de un usuario.
 * Usa semántica PUT (reemplazo total) — el ADMIN envía la lista definitiva.
 * Más simple y predecible que agregar/quitar roles individualmente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleAssignDTO {

    @NotNull(message = "Se debe especificar al menos un rol")
    @Size(min = 1, message = "El usuario debe conservar al menos un rol")
    private Set<String> roles;
}
