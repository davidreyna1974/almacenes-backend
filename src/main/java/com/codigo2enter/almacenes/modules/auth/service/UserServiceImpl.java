package com.codigo2enter.almacenes.modules.auth.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.core.exception.BusinessRuleException;
import com.codigo2enter.almacenes.core.exception.DuplicateResourceException;
import com.codigo2enter.almacenes.core.exception.ResourceNotFoundException;
import com.codigo2enter.almacenes.core.exception.TooManyAttemptsException;
import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.auth.dto.*;
import com.codigo2enter.almacenes.modules.auth.mapper.UserMapper;
import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;

    // ── Autenticación ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthRequestDTO request) {
        String username = request.getUsername();

        if (loginAttemptService.isBlocked(username)) {
            throw new TooManyAttemptsException(
                "Demasiados intentos fallidos. Intenta nuevamente en "
                    + loginAttemptService.getRemainingLockoutMinutes(username) + " minuto(s)."
            );
        }

        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas."));

            if (!user.isActive()) {
                throw new BadCredentialsException("Credenciales incorrectas.");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BadCredentialsException("Credenciales incorrectas.");
            }

            loginAttemptService.loginSucceeded(username);

            Set<String> roles = user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());

            String token = jwtUtils.generateToken(user.getUsername(), roles);
            return AuthResponseDTO.builder().token(token).build();
        } catch (BadCredentialsException ex) {
            loginAttemptService.loginFailed(username);
            throw ex;
        }
    }

    // ── Gestión de usuarios (ADMIN) ───────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        return userMapper.toResponseDTOList(userRepository.findByActiveTrue());
    }

    /**
     * {@inheritDoc}
     *
     * Sort por createdAt DESC para que los usuarios más recientes aparezcan primero.
     * Esto hace coherente la lista paginada con la expectativa del ADMIN al gestionar
     * el panel de usuarios.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<UserResponseDTO> getAllUsers(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> result = userRepository.findByActiveTrue(pageable);
        return PageResponseDTO.from(result.map(userMapper::toResponseDTO));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        return userMapper.toResponseDTO(findUserOrThrow(id));
    }

    @Override
    public UserResponseDTO createUser(UserCreateDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateResourceException(
                "El nombre de usuario '" + dto.getUsername() + "' ya está registrado.");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException(
                "El email '" + dto.getEmail() + "' ya está registrado.");
        }

        Set<Role> roles = resolveRoles(dto.getRoles());

        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .email(dto.getEmail())
                .roles(roles)
                .build();

        return userMapper.toResponseDTO(userRepository.save(user));
    }

    @Override
    public UserResponseDTO updateUser(Long id, UserUpdateDTO dto) {
        User user = findUserOrThrow(id);

        userRepository.findByUsernameAndIdNot(dto.getUsername(), id).ifPresent(u -> {
            throw new DuplicateResourceException(
                "El username '" + dto.getUsername() + "' ya está en uso por otro usuario.");
        });
        userRepository.findByEmailAndIdNot(dto.getEmail(), id).ifPresent(u -> {
            throw new DuplicateResourceException(
                "El email '" + dto.getEmail() + "' ya está en uso por otro usuario.");
        });

        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setUpdatedAt(LocalDateTime.now());
        return userMapper.toResponseDTO(user);
    }

    @Override
    public void deactivateUser(Long id) {
        User user = findUserOrThrow(id);

        String currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        if (user.getUsername().equals(currentUsername)) {
            throw new BusinessRuleException(
                "No puedes desactivar tu propia cuenta. " +
                "Pide a otro administrador que lo haga.");
        }

        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
    }

    @Override
    public UserResponseDTO assignRoles(Long id, UserRoleAssignDTO dto) {
        User user = findUserOrThrow(id);
        Set<Role> newRoles = resolveRoles(dto.getRoles());

        user.getRoles().clear();
        user.getRoles().addAll(newRoles);
        user.setUpdatedAt(LocalDateTime.now());

        // save() explícito necesario: la relación @ManyToMany (user_roles)
        // no se persiste con dirty-checking — requiere sincronización explícita.
        return userMapper.toResponseDTO(userRepository.save(user));
    }

    // ── Perfil propio ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getMyProfile() {
        return userMapper.toResponseDTO(resolveAuthenticatedUser());
    }

    @Override
    public void changePassword(ChangePasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessRuleException("Las contraseñas nuevas no coinciden.");
        }

        User user = resolveAuthenticatedUser();

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new BusinessRuleException("La contraseña actual es incorrecta.");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // ── Métodos privados ──────────────────────────────────────────────────

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Usuario con id " + id + " no encontrado o inactivo."));
    }

    private User resolveAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Usuario autenticado no encontrado en el sistema."));
    }

    /**
     * Convierte un Set<String> de nombres de rol a entidades Role.
     * Lanza BusinessRuleException si algún nombre no corresponde a un rol existente.
     */
    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            Role role = roleRepository.findByName(name)
                    .orElseThrow(() -> new BusinessRuleException(
                        "Rol '" + name + "' no existe. " +
                        "Roles válidos: ROLE_ADMIN, ROLE_MANAGER, ROLE_WAREHOUSEMAN, ROLE_SALES."));
            roles.add(role);
        }
        return roles;
    }
}
