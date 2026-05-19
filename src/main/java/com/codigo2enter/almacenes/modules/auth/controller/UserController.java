package com.codigo2enter.almacenes.modules.auth.controller;

import com.codigo2enter.almacenes.modules.auth.dto.AuthRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.AuthResponseDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;
import com.codigo2enter.almacenes.modules.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST que expone los endpoints públicos del módulo de autenticación.
 *
 * Prefijo base: /api/v1/auth
 * Todos los endpoints de esta clase están declarados como permitAll() en
 * SecurityConfig, por lo que no requieren token JWT para ser consumidos.
 *
 * Responsabilidad del controlador: recibir la petición HTTP, delegar al
 * servicio y devolver la respuesta con el código HTTP apropiado.
 * No contiene lógica de negocio.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    // UserService es una interfaz — Spring inyecta la implementación UserServiceImpl.
    // Depender de la interfaz y no de la clase concreta facilita los tests y
    // el intercambio de implementaciones sin tocar el controlador.
    private final UserService userService;

    /**
     * POST /api/v1/auth/register
     *
     * Registra un nuevo usuario en el sistema.
     * @Valid activa las validaciones de Jakarta definidas en UserRequestDTO
     * (@NotBlank, @Email, @Size) antes de que el método sea ejecutado.
     * Si alguna validación falla, Spring retorna automáticamente HTTP 400 Bad Request.
     *
     * @param request cuerpo JSON con username, email y password
     * @return 201 Created con el DTO público del usuario creado (sin contraseña)
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(@Valid @RequestBody UserRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerUser(request));
    }

    /**
     * POST /api/v1/auth/login
     *
     * Autentica a un usuario existente y devuelve un JWT firmado.
     * @Valid activa las validaciones de AuthRequestDTO (@NotBlank en username y password)
     * antes de invocar al servicio.
     *
     * ResponseEntity.ok() es equivalente a ResponseEntity.status(HttpStatus.OK).body(...)
     * y se usa aquí porque un login exitoso es una operación de consulta, no de creación,
     * por lo que HTTP 200 OK es el código semánticamente correcto (a diferencia del
     * registro que devuelve 201 Created).
     *
     * @param request cuerpo JSON con username y password
     * @return 200 OK con el DTO que contiene el token JWT listo para usar
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(userService.login(request));
    }
}
