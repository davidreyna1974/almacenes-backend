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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación concreta de UserService.
 *
 * @RequiredArgsConstructor genera automáticamente un constructor con todos
 * los campos 'final', que Spring usa para inyectar las dependencias.
 * Esto evita el uso de @Autowired y facilita las pruebas unitarias.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    // JwtUtils vive en core/security y se inyecta aquí para generar el token
    // tras una autenticación exitosa.
    private final JwtUtils jwtUtils;

    @Override
    public UserResponseDTO registerUser(UserRequestDTO request) {

        // Validar reglas de negocio contra duplicados
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("El nombre de usuario '" + request.getUsername() + "' ya está registrado en el sistema.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El correo electrónico '" + request.getEmail() + "' ya está registrado en el sistema.");
        }

        // Convertir DTO de entrada a Entidad JPA usando MapStruct
        User userEntity = userMapper.toEntity(request);

        // El password se cifra con BCrypt antes de persistirlo.
        // passwordEncoder.encode() genera un hash único con salt aleatorio
        // cada vez que se llama, por lo que dos registros con la misma
        // contraseña producen hashes distintos.
        userEntity.setPassword(passwordEncoder.encode(request.getPassword()));

        // Buscamos la entidad del rol en la base de datos para asegurar su consistencia
        Role defaultRole = roleRepository.findByName("ROLE_WAREHOUSEMAN")
                .orElseThrow(() -> new RuntimeException("Error: El rol 'ROLE_WAREHOUSEMAN' no existe en el sistema."));

        // Accedemos al HashSet de roles del usuario (gracias al new HashSet<>())
        // y le inyectamos el rol por defecto
        userEntity.getRoles().add(defaultRole);

        // Persistir en la base de datos
        User savedUser = userRepository.save(userEntity);

        // Retornar el DTO de salida seguro, libre de contraseñas
        return userMapper.toResponseDTO(savedUser);
    }

    /**
     * Autentica al usuario verificando sus credenciales y genera un JWT firmado.
     *
     * Se marca como readOnly = true porque este método solo lee de la base de datos.
     * Esto permite que Hibernate optimice la sesión (sin flush al final) y que
     * el pool de conexiones pueda enrutar la consulta a réplicas de lectura si existieran.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthRequestDTO request) {

        // Buscamos el usuario por username. Si no existe devolvemos el mismo mensaje
        // que si la contraseña fuera incorrecta — evitar enumerar usuarios válidos
        // (técnica de seguridad: no revelar qué parte de las credenciales falló).
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Credenciales incorrectas."));

        // passwordEncoder.matches() aplica el mismo algoritmo BCrypt al password
        // en texto plano y lo compara con el hash almacenado en la base de datos.
        // Nunca se desencripta el hash — BCrypt es una función unidireccional.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Credenciales incorrectas.");
        }

        // Extraemos solo los nombres de los roles (ej. "ROLE_WAREHOUSEMAN") para
        // incluirlos como claim en el JWT. JwtUtils trabaja con String para
        // no acoplarse a la entidad Role del módulo auth.
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        // Generamos el token JWT firmado con HMAC-SHA256, con vigencia de 2 horas.
        // El token contiene username y roles — suficiente para autorizar peticiones
        // posteriores sin consultar la base de datos en cada request.
        String token = jwtUtils.generateToken(user.getUsername(), roles);

        return AuthResponseDTO.builder()
                .token(token)
                .build();
    }
}
