package com.codigo2enter.almacenes.modules.auth.service;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.auth.dto.AuthRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.AuthResponseDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;
import com.codigo2enter.almacenes.modules.auth.mapper.UserMapper;
import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @InjectMocks UserServiceImpl userService;

    private UserRequestDTO registerRequest;
    private User user;
    private Role defaultRole;

    @BeforeEach
    void setUp() {
        registerRequest = UserRequestDTO.builder()
                .username("tester01")
                .password("Admin123!")
                .email("tester01@almacenes.com")
                .build();

        defaultRole = Role.builder().id(1L).name("ROLE_WAREHOUSEMAN").build();

        user = User.builder()
                .id(1L).username("tester01")
                .password("$2a$10$hashedPassword")
                .email("tester01@almacenes.com")
                .roles(new HashSet<>(Set.of(defaultRole)))
                .build();

        lenient().when(roleRepository.findByName("ROLE_WAREHOUSEMAN"))
                .thenReturn(Optional.of(defaultRole));
        lenient().when(userMapper.toEntity(any())).thenReturn(user);
        lenient().when(userRepository.save(any())).thenReturn(user);
        lenient().when(userMapper.toResponseDTO(any()))
                .thenReturn(UserResponseDTO.builder().id(1L).username("tester01").build());
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedPassword");
    }

    // ── registerUser ─────────────────────────────────────────────────────────

    @Test
    void registerUser_credencialesUnicas_debeCrearUsuarioConRolPorDefecto() {
        when(userRepository.existsByUsername("tester01")).thenReturn(false);
        when(userRepository.existsByEmail("tester01@almacenes.com")).thenReturn(false);

        UserResponseDTO result = userService.registerUser(registerRequest);

        assertNotNull(result);
        verify(passwordEncoder).encode("Admin123!");
        verify(userRepository).save(user);
        // Verifica que el rol por defecto fue asignado al usuario
        assertTrue(user.getRoles().contains(defaultRole));
    }

    @Test
    void registerUser_usernameDuplicado_debeLanzarExcepcion() {
        when(userRepository.existsByUsername("tester01")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> userService.registerUser(registerRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_emailDuplicado_debeLanzarExcepcion() {
        when(userRepository.existsByUsername("tester01")).thenReturn(false);
        when(userRepository.existsByEmail("tester01@almacenes.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> userService.registerUser(registerRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_rolNoExiste_debeLanzarExcepcion() {
        when(userRepository.existsByUsername("tester01")).thenReturn(false);
        when(userRepository.existsByEmail("tester01@almacenes.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_WAREHOUSEMAN")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.registerUser(registerRequest));
        verify(userRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_credencialesCorrectas_debeRetornarToken() {
        AuthRequestDTO request = new AuthRequestDTO("tester01", "Admin123!");
        when(userRepository.findByUsername("tester01")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Admin123!", user.getPassword())).thenReturn(true);
        when(jwtUtils.generateToken(eq("tester01"), anySet())).thenReturn("jwt.token.aqui");

        AuthResponseDTO result = userService.login(request);

        assertEquals("jwt.token.aqui", result.getToken());
        verify(jwtUtils).generateToken(eq("tester01"), anySet());
    }

    @Test
    void login_usuarioNoExiste_debeLanzarExcepcionGenérica() {
        // El mensaje es genérico ("Credenciales incorrectas") — no revela si fue
        // el usuario o la contraseña lo que falló (protege contra enumeración de usuarios).
        AuthRequestDTO request = new AuthRequestDTO("inexistente", "cualquiera");
        when(userRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login(request));

        assertEquals("Credenciales incorrectas.", ex.getMessage());
        verify(jwtUtils, never()).generateToken(anyString(), anySet());
    }

    @Test
    void login_passwordIncorrecto_debeLanzarExcepcionGenérica() {
        AuthRequestDTO request = new AuthRequestDTO("tester01", "wrongPassword");
        when(userRepository.findByUsername("tester01")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", user.getPassword())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login(request));

        // El mensaje es idéntico al de usuario no encontrado — no filtra información
        assertEquals("Credenciales incorrectas.", ex.getMessage());
        verify(jwtUtils, never()).generateToken(anyString(), anySet());
    }

    @Test
    void login_rolesIncluídosEnToken() {
        AuthRequestDTO request = new AuthRequestDTO("tester01", "Admin123!");
        when(userRepository.findByUsername("tester01")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Admin123!", user.getPassword())).thenReturn(true);
        when(jwtUtils.generateToken(anyString(), anySet())).thenReturn("token");

        userService.login(request);

        // Verifica que generateToken recibe el conjunto de roles del usuario
        verify(jwtUtils).generateToken(eq("tester01"), eq(Set.of("ROLE_WAREHOUSEMAN")));
    }
}
