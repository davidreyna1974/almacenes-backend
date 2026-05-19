package com.codigo2enter.almacenes.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para el endpoint de autenticación POST /api/v1/auth/login.
 *
 * Transporta las credenciales que el cliente (Angular) envía en el body
 * de la petición HTTP. Spring valida los campos antes de que el controlador
 * invoque al servicio, gracias a @Valid en el parámetro del endpoint.
 *
 * Este objeto NO llega a la base de datos — solo se usa para verificar
 * credenciales contra el usuario almacenado y generar el JWT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequestDTO {

    // Nombre de usuario con el que está registrado en la tabla 'users'.
    // @NotBlank rechaza null, cadena vacía y cadenas de solo espacios.
    @NotBlank
    private String username;

    // Contraseña en texto plano enviada por el cliente.
    // El servicio la comparará contra el hash BCrypt almacenado en la base de datos.
    // Nunca se persiste ni se incluye en ningún DTO de respuesta.
    @NotBlank
    private String password;
}
