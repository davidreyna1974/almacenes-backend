package com.codigo2enter.almacenes.modules.auth.service;

import com.codigo2enter.almacenes.modules.auth.dto.UserRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;
import com.codigo2enter.almacenes.modules.auth.mapper.UserMapper;
import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

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

        // [ZONA DE CONTROL]: Aquí se interceptará el password para cifrarlo con BCrypt más adelante
        userEntity.setPassword(request.getPassword());

        // Buscamos la entidad del rol en la base de datos para asegurar su consistencia
        Role defaultRole = roleRepository.findByName("ROLE_WAREHOUSEMAN")
                .orElseThrow(() -> new RuntimeException("Error: El rol 'ROLE_WAREHOUSEMAN' no existe en el sistema."));

        // Accedemos al HashSet de roles del usuario (gracias al new HashSet<>()) 
        // y le inyectamos el rol por defecto
        userEntity.getRoles().add(defaultRole);

        // Persistir en la base de datos (Ahora el usuario viaja con su rol amarrado)
        User savedUser = userRepository.save(userEntity);

        // Retornar el DTO de salida seguro libre de contraseñas
        return userMapper.toResponseDTO(savedUser);
    }
}
