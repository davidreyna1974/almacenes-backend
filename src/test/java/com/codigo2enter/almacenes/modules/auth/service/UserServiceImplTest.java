package com.codigo2enter.almacenes.modules.auth.service;

import com.codigo2enter.almacenes.core.exception.TooManyAttemptsException;
import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.auth.dto.*;
import com.codigo2enter.almacenes.modules.auth.mapper.UserMapper;
import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

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
    @Mock LoginAttemptService loginAttemptService;
    @InjectMocks UserServiceImpl userService;

    private User user;
    private Role defaultRole;
    private Role adminRole;
    private UserResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("admin");
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        defaultRole = Role.builder().id(2L).name("ROLE_WAREHOUSEMAN").build();
        adminRole   = Role.builder().id(1L).name("ROLE_ADMIN").build();

        user = User.builder()
                .id(1L).username("tester").password("$2a$10$hash")
                .email("t@t.com").active(true)
                .roles(new HashSet<>(Set.of(defaultRole))).build();

        responseDTO = UserResponseDTO.builder()
                .id(1L).username("tester").email("t@t.com").active(true)
                .roles(Set.of("ROLE_WAREHOUSEMAN")).build();

        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
            User.builder().id(99L).username("admin").password("hash").active(true)
                .roles(new HashSet<>(Set.of(adminRole))).build()));
        lenient().when(roleRepository.findByName("ROLE_WAREHOUSEMAN")).thenReturn(Optional.of(defaultRole));
        lenient().when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        lenient().when(userMapper.toResponseDTO(any())).thenReturn(responseDTO);
        lenient().when(userMapper.toResponseDTOList(any())).thenReturn(List.of(responseDTO));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        lenient().when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    void login_credencialesCorrectas_debeRetornarToken() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass123!", user.getPassword())).thenReturn(true);
        when(jwtUtils.generateToken(eq("tester"), anySet())).thenReturn("jwt.token");

        AuthResponseDTO result = userService.login(new AuthRequestDTO("tester", "Pass123!"));
        assertEquals("jwt.token", result.getToken());
    }

    @Test
    void login_usuarioInactivo_debeLanzarExcepcion() {
        user.setActive(false);
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login(new AuthRequestDTO("tester", "Pass123!")));
        assertEquals("Credenciales incorrectas.", ex.getMessage());
    }

    @Test
    void login_passwordIncorrecto_debeLanzarExcepcionGenerica() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login(new AuthRequestDTO("tester", "wrong")));
        assertEquals("Credenciales incorrectas.", ex.getMessage());
        verify(jwtUtils, never()).generateToken(anyString(), anySet());
    }

    @Test
    void login_usuarioNoExiste_debeLanzarExcepcionGenerica() {
        when(userRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login(new AuthRequestDTO("nadie", "pass")));
        assertEquals("Credenciales incorrectas.", ex.getMessage());
    }

    @Test
    void login_rolesIncluidos_enToken() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass123!", user.getPassword())).thenReturn(true);
        when(jwtUtils.generateToken(anyString(), anySet())).thenReturn("tok");

        userService.login(new AuthRequestDTO("tester", "Pass123!"));
        verify(jwtUtils).generateToken(eq("tester"), eq(Set.of("ROLE_WAREHOUSEMAN")));
    }

    @Test
    void login_usuarioBloqueado_debeLanzarTooManyAttempts() {
        when(loginAttemptService.isBlocked("tester")).thenReturn(true);
        when(loginAttemptService.getRemainingLockoutMinutes("tester")).thenReturn(15L);

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class,
                () -> userService.login(new AuthRequestDTO("tester", "Pass123!")));
        assertTrue(ex.getMessage().contains("15"));
        verify(userRepository, never()).findByUsername("tester");
    }

    @Test
    void login_passwordIncorrecto_debeRegistrarIntentoFallido() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> userService.login(new AuthRequestDTO("tester", "wrong")));
        verify(loginAttemptService).loginFailed("tester");
        verify(loginAttemptService, never()).loginSucceeded(anyString());
    }

    @Test
    void login_credencialesCorrectas_debeReiniciarIntentos() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass123!", user.getPassword())).thenReturn(true);
        when(jwtUtils.generateToken(anyString(), anySet())).thenReturn("tok");

        userService.login(new AuthRequestDTO("tester", "Pass123!"));
        verify(loginAttemptService).loginSucceeded("tester");
        verify(loginAttemptService, never()).loginFailed(anyString());
    }

    // ── createUser ────────────────────────────────────────────────────────

    @Test
    void createUser_credencialesUnicas_debeCrear() {
        when(userRepository.existsByUsername("nuevo")).thenReturn(false);
        when(userRepository.existsByEmail("n@n.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        UserCreateDTO dto = UserCreateDTO.builder()
                .username("nuevo").password("Pass123!").email("n@n.com")
                .roles(Set.of("ROLE_WAREHOUSEMAN")).build();

        UserResponseDTO result = userService.createUser(dto);
        assertNotNull(result);
        verify(passwordEncoder).encode("Pass123!");
    }

    @Test
    void createUser_usernameDuplicado_debeLanzarExcepcion() {
        when(userRepository.existsByUsername("tester")).thenReturn(true);

        UserCreateDTO dto = UserCreateDTO.builder()
                .username("tester").password("P1!").email("x@x.com")
                .roles(Set.of("ROLE_WAREHOUSEMAN")).build();

        assertThrows(RuntimeException.class, () -> userService.createUser(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rolInexistente_debeLanzarExcepcion() {
        when(userRepository.existsByUsername("nuevo")).thenReturn(false);
        when(userRepository.existsByEmail("n@n.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_INEXISTENTE")).thenReturn(Optional.empty());

        UserCreateDTO dto = UserCreateDTO.builder()
                .username("nuevo").password("Pass123!").email("n@n.com")
                .roles(Set.of("ROLE_INEXISTENTE")).build();

        assertThrows(RuntimeException.class, () -> userService.createUser(dto));
        verify(userRepository, never()).save(any());
    }

    // ── updateUser ────────────────────────────────────────────────────────

    @Test
    void updateUser_datosValidos_debeActualizarConAuditoria() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameAndIdNot("tester2", 1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndIdNot("t2@t.com", 1L)).thenReturn(Optional.empty());

        UserUpdateDTO dto = UserUpdateDTO.builder().username("tester2").email("t2@t.com").build();
        userService.updateUser(1L, dto);

        assertEquals("tester2", user.getUsername());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    void updateUser_usernameDuplicado_debeLanzarExcepcion() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        User otro = User.builder().id(2L).username("otro").build();
        when(userRepository.findByUsernameAndIdNot("otro", 1L)).thenReturn(Optional.of(otro));

        UserUpdateDTO dto = UserUpdateDTO.builder().username("otro").email("x@x.com").build();
        assertThrows(RuntimeException.class, () -> userService.updateUser(1L, dto));
    }

    // ── deactivateUser ────────────────────────────────────────────────────

    @Test
    void deactivateUser_exitoso_debeSetearActiveEnFalse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivateUser(1L);

        assertFalse(user.isActive());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    void deactivateUser_propioUsuario_debeLanzarExcepcion() {
        // El usuario "admin" intenta desactivarse a sí mismo
        User adminUser = User.builder().id(99L).username("admin").active(true)
                .roles(new HashSet<>()).build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));

        assertThrows(RuntimeException.class, () -> userService.deactivateUser(99L));
        assertTrue(adminUser.isActive());
    }

    // ── assignRoles ───────────────────────────────────────────────────────

    @Test
    void assignRoles_reemplazaRolesCompletos() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UserRoleAssignDTO dto = UserRoleAssignDTO.builder()
                .roles(Set.of("ROLE_ADMIN","ROLE_WAREHOUSEMAN")).build();

        userService.assignRoles(1L, dto);

        assertTrue(user.getRoles().contains(adminRole));
        assertTrue(user.getRoles().contains(defaultRole));
        verify(userRepository).save(user);
    }

    @Test
    void assignRoles_rolInexistente_debeLanzarExcepcion() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_FAKE")).thenReturn(Optional.empty());

        UserRoleAssignDTO dto = UserRoleAssignDTO.builder().roles(Set.of("ROLE_FAKE")).build();
        assertThrows(RuntimeException.class, () -> userService.assignRoles(1L, dto));
    }

    // ── changePassword ────────────────────────────────────────────────────

    @Test
    void changePassword_correcta_debeActualizar() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
            User.builder().id(99L).username("admin").password("old_hash").active(true)
                .roles(new HashSet<>()).build()));
        when(passwordEncoder.matches("OldPass1!", "old_hash")).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);

        ChangePasswordDTO dto = ChangePasswordDTO.builder()
                .currentPassword("OldPass1!").newPassword("NewPass1!").confirmPassword("NewPass1!").build();

        assertDoesNotThrow(() -> userService.changePassword(dto));
        verify(passwordEncoder).encode("NewPass1!");
    }

    @Test
    void changePassword_confirmacionNoCoincide_debeLanzarExcepcion() {
        ChangePasswordDTO dto = ChangePasswordDTO.builder()
                .currentPassword("OldPass1!").newPassword("NewPass1!").confirmPassword("Diferente!").build();

        assertThrows(RuntimeException.class, () -> userService.changePassword(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_passwordActualIncorrecto_debeLanzarExcepcion() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
            User.builder().id(99L).username("admin").password("old_hash").active(true)
                .roles(new HashSet<>()).build()));
        when(passwordEncoder.matches("WrongPass!", "old_hash")).thenReturn(false);

        ChangePasswordDTO dto = ChangePasswordDTO.builder()
                .currentPassword("WrongPass!").newPassword("NewPass1!").confirmPassword("NewPass1!").build();

        assertThrows(RuntimeException.class, () -> userService.changePassword(dto));
        verify(userRepository, never()).save(any());
    }

    // ── getMyProfile ──────────────────────────────────────────────────────

    @Test
    void getMyProfile_debeRetornarUsuarioAutenticado() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
            User.builder().id(99L).username("admin").active(true).roles(new HashSet<>()).build()));

        UserResponseDTO result = userService.getMyProfile();
        assertNotNull(result);
    }

    // ── getAllUsers ────────────────────────────────────────────────────────

    @Test
    void getAllUsers_debeRetornarSoloActivos() {
        when(userRepository.findByActiveTrue()).thenReturn(List.of(user));

        List<UserResponseDTO> result = userService.getAllUsers();
        assertEquals(1, result.size());
    }

    @Test
    void getUserById_noExistente_debeLanzarExcepcion() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getUserById(99L));
    }
}
