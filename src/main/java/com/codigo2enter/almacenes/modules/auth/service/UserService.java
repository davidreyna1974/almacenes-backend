package com.codigo2enter.almacenes.modules.auth.service;

import com.codigo2enter.almacenes.modules.auth.dto.AuthRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.AuthResponseDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;

/**
 * Contrato de la capa de servicio para la gestión de usuarios y autenticación.
 *
 * Define las operaciones públicas disponibles para los controladores REST.
 * La implementación concreta vive en UserServiceImpl, lo que permite
 * desacoplar la interfaz del detalle técnico (JPA, BCrypt, JWT).
 */
public interface UserService {

    /**
     * Registra un nuevo usuario en el sistema asignándole el rol por defecto.
     *
     * @param request datos del nuevo usuario (username, email, password)
     * @return DTO con la información pública del usuario creado
     */
    UserResponseDTO registerUser(UserRequestDTO request);

    /**
     * Autentica a un usuario existente y genera un JWT firmado.
     *
     * @param request credenciales enviadas por el cliente (username, password)
     * @return DTO con el token JWT listo para usar en cabeceras Authorization
     */
    AuthResponseDTO login(AuthRequestDTO request);
}
