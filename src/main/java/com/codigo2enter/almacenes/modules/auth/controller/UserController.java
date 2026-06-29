package com.codigo2enter.almacenes.modules.auth.controller;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.auth.dto.*;
import com.codigo2enter.almacenes.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



/**
 * Controlador REST para autenticación y gestión de usuarios.
 *
 * Tres grupos de endpoints con distintos niveles de acceso
 * (reglas definidas en SecurityConfig, no aquí):
 *
 *   1. Autenticación pública: POST /login (permitAll)
 *   2. Gestión de usuarios: /users/** (hasRole ADMIN)
 *   3. Perfil propio: /me/** (authenticated)
 */
@Tag(name = "Auth", description = "Autenticación y gestión de usuarios")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Autenticación pública ─────────────────────────────────────────────

    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario y retorna JWT + datos básicos del perfil")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Autenticado — incluye token JWT"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "401", description = "Credenciales incorrectas") })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(userService.login(request));
    }

    // ── Gestión de usuarios (ADMIN only — protegido en SecurityConfig) ────

    @Operation(summary = "Listar usuarios", description = "Retorna todos los usuarios del sistema paginados — solo ADMIN")
    @ApiResponse(responseCode = "200", description = "Página de usuarios")
    @GetMapping("/users")
    public ResponseEntity<PageResponseDTO<UserResponseDTO>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.getAllUsers(page, size));
    }

    @Operation(summary = "Crear usuario", description = "Registra un nuevo usuario con rol(es) asignados — solo ADMIN")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Usuario creado"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "409", description = "Username o email ya existe") })
    @PostMapping("/users")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto));
    }

    @Operation(summary = "Obtener usuario por ID", description = "Devuelve los datos completos de un usuario — solo ADMIN")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado") })
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "Actualizar usuario", description = "Actualiza datos editables de un usuario (nombre, email, estado) — solo ADMIN")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                    @ApiResponse(responseCode = "409", description = "Email ya en uso por otro usuario") })
    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable Long id,
                                                       @Valid @RequestBody UserUpdateDTO dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @Operation(summary = "Desactivar usuario", description = "Soft delete — el usuario no puede autenticarse pero su historial se preserva — solo ADMIN")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Usuario desactivado"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado") })
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /users/{id}/roles — reemplaza TODOS los roles del usuario.
     * Semántica PUT: el ADMIN envía la lista definitiva de roles.
     */
    @Operation(summary = "Asignar roles a usuario", description = "Reemplaza todos los roles del usuario con la lista enviada (semántica PUT) — solo ADMIN")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Roles asignados"),
                    @ApiResponse(responseCode = "400", description = "Lista de roles inválida"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado") })
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserResponseDTO> assignRoles(@PathVariable Long id,
                                                        @Valid @RequestBody UserRoleAssignDTO dto) {
        return ResponseEntity.ok(userService.assignRoles(id, dto));
    }

    // ── Perfil propio (cualquier autenticado — protegido en SecurityConfig) ─

    @Operation(summary = "Obtener mi perfil", description = "Devuelve los datos del usuario autenticado extraídos del JWT")
    @ApiResponse(responseCode = "200", description = "Perfil del usuario autenticado")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    /**
     * PUT /me/password — cambia la contraseña del usuario autenticado.
     * 204 sin body — la contraseña no se expone en la respuesta.
     */
    @Operation(summary = "Cambiar contraseña", description = "Cambia la contraseña del usuario autenticado — requiere contraseña actual para confirmar identidad")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Contraseña cambiada"),
                    @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o nueva inválida") })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return ResponseEntity.noContent().build();
    }
}
