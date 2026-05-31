package com.codigo2enter.almacenes.modules.auth.controller;

import com.codigo2enter.almacenes.modules.auth.dto.*;
import com.codigo2enter.almacenes.modules.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Autenticación pública ─────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(userService.login(request));
    }

    // ── Gestión de usuarios (ADMIN only — protegido en SecurityConfig) ────

    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable Long id,
                                                       @Valid @RequestBody UserUpdateDTO dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /users/{id}/roles — reemplaza TODOS los roles del usuario.
     * Semántica PUT: el ADMIN envía la lista definitiva de roles.
     */
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserResponseDTO> assignRoles(@PathVariable Long id,
                                                        @Valid @RequestBody UserRoleAssignDTO dto) {
        return ResponseEntity.ok(userService.assignRoles(id, dto));
    }

    // ── Perfil propio (cualquier autenticado — protegido en SecurityConfig) ─

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    /**
     * PUT /me/password — cambia la contraseña del usuario autenticado.
     * 204 sin body — la contraseña no se expone en la respuesta.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return ResponseEntity.noContent().build();
    }
}
