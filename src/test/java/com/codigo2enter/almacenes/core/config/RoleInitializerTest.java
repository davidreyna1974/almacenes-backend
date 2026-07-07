package com.codigo2enter.almacenes.core.config;

import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del {@link RoleInitializer}.
 *
 * Verifican la lógica de sembrado de roles de referencia sin tocar la BD (mock del
 * repositorio): que crea los roles que faltan, que es idempotente cuando ya existen,
 * y que solo crea los faltantes cuando la siembra es parcial.
 */
@ExtendWith(MockitoExtension.class)
class RoleInitializerTest {

    @Mock
    RoleRepository roleRepository;

    @InjectMocks
    RoleInitializer roleInitializer;

    @Test
    void ensureRoles_bdSinRoles_creaLosCuatroRoles() {
        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());

        roleInitializer.ensureRoles();

        verify(roleRepository, times(RoleInitializer.REQUIRED_ROLES.size())).save(any(Role.class));
        RoleInitializer.REQUIRED_ROLES.forEach(name ->
                verify(roleRepository).save(argThat(r -> name.equals(r.getName()))));
    }

    @Test
    void ensureRoles_rolesYaExisten_noCreaNinguno_idempotente() {
        when(roleRepository.findByName(anyString()))
                .thenAnswer(inv -> Optional.of(Role.builder().name(inv.getArgument(0)).build()));

        roleInitializer.ensureRoles();

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void ensureRoles_faltaUnRol_creaSoloElFaltante() {
        when(roleRepository.findByName(anyString()))
                .thenAnswer(inv -> "ROLE_SALES".equals(inv.getArgument(0))
                        ? Optional.empty()
                        : Optional.of(Role.builder().name(inv.getArgument(0)).build()));

        roleInitializer.ensureRoles();

        verify(roleRepository, times(1)).save(any(Role.class));
        verify(roleRepository).save(argThat(r -> "ROLE_SALES".equals(r.getName())));
    }
}
