package com.codigo2enter.almacenes.modules.auth.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.auth.dto.*;

import java.util.List;

/**
 * Contrato de la capa de servicio para gestión de usuarios y autenticación.
 *
 * Agrupa tres responsabilidades:
 *   1. Autenticación pública (login)
 *   2. Gestión de usuarios — solo ADMIN (CRUD + asignación de roles)
 *   3. Operaciones de perfil propio — cualquier autenticado
 */
public interface UserService {

    // ── Autenticación (pública) ───────────────────────────────────────────

    /**
     * Autentica a un usuario existente y genera un JWT firmado.
     * El JWT incluye username y roles para autorización stateless.
     */
    AuthResponseDTO login(AuthRequestDTO request);

    // ── Gestión de usuarios (solo ADMIN) ─────────────────────────────────

    /** Retorna todos los usuarios activos del sistema. */
    List<UserResponseDTO> getAllUsers();

    /**
     * Retorna una página de usuarios activos, ordenados por fecha de creación descendente.
     *
     * @param page número de página (base 0)
     * @param size cantidad de registros por página
     * @return PageResponseDTO con los usuarios de la página solicitada
     */
    PageResponseDTO<UserResponseDTO> getAllUsers(int page, int size);

    /** Retorna el detalle de un usuario por ID. */
    UserResponseDTO getUserById(Long id);

    /**
     * Crea un nuevo usuario con el rol especificado por el ADMIN.
     * Reemplaza el antiguo registerUser() público.
     */
    UserResponseDTO createUser(UserCreateDTO dto);

    /** Actualiza username y email de un usuario existente. */
    UserResponseDTO updateUser(Long id, UserUpdateDTO dto);

    /**
     * Desactiva lógicamente un usuario (soft delete).
     * No se puede desactivar al propio usuario autenticado.
     */
    void deactivateUser(Long id);

    /**
     * Reemplaza la lista completa de roles de un usuario.
     * Semántica PUT: el ADMIN envía la lista definitiva.
     */
    UserResponseDTO assignRoles(Long id, UserRoleAssignDTO dto);

    // ── Perfil propio (cualquier autenticado) ─────────────────────────────

    /** Retorna el perfil del usuario autenticado. */
    UserResponseDTO getMyProfile();

    /**
     * Cambia la contraseña del usuario autenticado.
     * Requiere contraseña actual para verificar identidad.
     */
    void changePassword(ChangePasswordDTO dto);
}
