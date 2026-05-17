package com.codigo2enter.almacenes.modules.auth.mapper;

import com.codigo2enter.almacenes.modules.auth.dto.UserRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;
import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.model.User;

import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/**
 * Mapper profesional gestionado por Spring Utilizando MapStruct.
 * Automatiza la conversion bidireccional entre Modelos JPA y Objetos DTO.
 */

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRolesToStrings")
    UserResponseDTO toResponseDTO(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateUserFromDTO(UserRequestDTO dto, @MappingTarget User user);

    /**
     * Metodo personalizado para aplanar los objetos Role a cadenas de texto simples.
     */
    @Named("mapRolesToStrings")
    default Set<String> mapRolesToStrings(Set<Role> roles) {
        if (roles == null) {
            return null;
        }
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}
