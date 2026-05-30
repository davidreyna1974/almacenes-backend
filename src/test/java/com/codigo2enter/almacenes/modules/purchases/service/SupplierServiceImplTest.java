package com.codigo2enter.almacenes.modules.purchases.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.purchases.dto.SupplierDTO;
import com.codigo2enter.almacenes.modules.purchases.mapper.SupplierMapper;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrder;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderStatus;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderRepository;
import com.codigo2enter.almacenes.modules.purchases.repository.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para SupplierServiceImpl.
 * Cubre 13 casos: Happy Path, Edge Case y Error Case por cada método.
 * Sin contexto de Spring — Mockito instancia solo la clase bajo prueba.
 *
 * SecurityContextHolder se configura en @BeforeEach y se limpia en @AfterEach
 * para simular el usuario autenticado extraído del JWT en métodos de escritura.
 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock private SupplierRepository      supplierRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private UserRepository          userRepository;
    @Mock private SupplierMapper          supplierMapper;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    private User        user;
    private SupplierDTO inputDTO;
    private Supplier    entity;
    private SupplierDTO outputDTO;

    @BeforeEach
    void setUp() {
        // SecurityContextHolder — lenient() porque los tests de consulta no lo usan
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("operador01");
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        user = User.builder()
                .id(1L).username("operador01").password("hashed").build();

        // Stub para resolveAuthenticatedUser() en createSupplier, updateSupplier y deactivateSupplier.
        lenient().when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));

        inputDTO = SupplierDTO.builder()
                .rfc("ABC123456789A")
                .companyName("Ferretería SA")
                .email("info@ferreteria.com")
                .active(true)
                .build();

        entity = Supplier.builder()
                .id(1L)
                .rfc("ABC123456789A")
                .companyName("Ferretería SA")
                .email("info@ferreteria.com")
                .active(true)
                .build();

        outputDTO = SupplierDTO.builder()
                .id(1L)
                .rfc("ABC123456789A")
                .companyName("Ferretería SA")
                .active(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // createSupplier
    // =========================================================================

    /**
     * Happy Path: RFC y razón social únicos → proveedor creado correctamente.
     * Verifica que ambas validaciones pasan, save() se invoca exactamente una vez
     * y createdBy queda asignado al usuario autenticado.
     */
    @Test
    @DisplayName("createSupplier: debe crear el proveedor cuando RFC y razón social son únicos")
    void shouldCreateSupplierSuccessfully() {
        // ARRANGE
        when(supplierRepository.existsByRfc("ABC123456789A")).thenReturn(false);
        when(supplierRepository.existsByCompanyName("Ferretería SA")).thenReturn(false);
        when(supplierMapper.toEntity(inputDTO)).thenReturn(entity);
        when(supplierRepository.save(entity)).thenReturn(entity);
        when(supplierMapper.toDTO(entity)).thenReturn(outputDTO);

        // ACT
        SupplierDTO result = supplierService.createSupplier(inputDTO);

        // ASSERT
        assertNotNull(result);
        assertEquals("ABC123456789A", result.getRfc());
        assertEquals(user, entity.getCreatedBy());
        verify(supplierRepository, times(1)).save(entity);
    }

    /**
     * Error Case: el RFC ya pertenece a otro proveedor.
     * save() nunca debe invocarse cuando la validación de RFC falla.
     */
    @Test
    @DisplayName("createSupplier: debe lanzar excepción cuando el RFC ya existe")
    void shouldThrowWhenRfcAlreadyExists() {
        // ARRANGE
        when(supplierRepository.existsByRfc("ABC123456789A")).thenReturn(true);

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> supplierService.createSupplier(inputDTO));
        verify(supplierRepository, never()).save(any());
    }

    /**
     * Error Case: la razón social ya está registrada en otro proveedor.
     * Valida la segunda regla de unicidad: el RFC puede ser único pero
     * si el nombre ya existe la operación también debe rechazarse.
     */
    @Test
    @DisplayName("createSupplier: debe lanzar excepción cuando la razón social ya existe")
    void shouldThrowWhenCompanyNameAlreadyExists() {
        // ARRANGE
        when(supplierRepository.existsByRfc("ABC123456789A")).thenReturn(false);
        when(supplierRepository.existsByCompanyName("Ferretería SA")).thenReturn(true);

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> supplierService.createSupplier(inputDTO));
        verify(supplierRepository, never()).save(any());
    }

    // =========================================================================
    // getAllActiveSuppliers
    // =========================================================================

    /**
     * Happy Path: retorna todos los proveedores activos.
     * Verifica que el mapper de lista es invocado y la respuesta tiene
     * el tamaño correcto.
     */
    @Test
    @DisplayName("getAllActiveSuppliers: debe retornar la lista de proveedores activos")
    void shouldReturnAllActiveSuppliers() {
        // ARRANGE
        Supplier entity2 = Supplier.builder().id(2L).companyName("Distribuidora XYZ").active(true).build();
        SupplierDTO output2 = SupplierDTO.builder().id(2L).companyName("Distribuidora XYZ").build();
        when(supplierRepository.findByActiveTrue()).thenReturn(List.of(entity, entity2));
        when(supplierMapper.toDTOList(anyList())).thenReturn(List.of(outputDTO, output2));

        // ACT
        List<SupplierDTO> result = supplierService.getAllActiveSuppliers();

        // ASSERT
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(supplierMapper, times(1)).toDTOList(anyList());
    }

    /**
     * Edge Case: no hay proveedores activos → lista vacía válida.
     * El servicio NO debe lanzar excepción ante un catálogo vacío.
     */
    @Test
    @DisplayName("getAllActiveSuppliers: debe retornar lista vacía cuando no hay proveedores activos")
    void shouldReturnEmptyListWhenNoActiveSuppliers() {
        // ARRANGE
        when(supplierRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(supplierMapper.toDTOList(anyList())).thenReturn(Collections.emptyList());

        // ACT
        List<SupplierDTO> result = supplierService.getAllActiveSuppliers();

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // findById
    // =========================================================================

    /**
     * Happy Path: proveedor encontrado por ID.
     */
    @Test
    @DisplayName("findById: debe retornar el proveedor cuando existe")
    void shouldReturnSupplierById() {
        // ARRANGE
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(supplierMapper.toDTO(entity)).thenReturn(outputDTO);

        // ACT
        SupplierDTO result = supplierService.findById(1L);

        // ASSERT
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    /**
     * Error Case: ID inexistente → RuntimeException con mensaje claro.
     * Un ID inválido no debe producir NullPointerException silenciosa.
     */
    @Test
    @DisplayName("findById: debe lanzar excepción cuando el proveedor no existe")
    void shouldThrowWhenSupplierNotFound() {
        // ARRANGE
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class, () -> supplierService.findById(99L));
    }

    // =========================================================================
    // updateSupplier
    // =========================================================================

    /**
     * Happy Path: el proveedor actualiza sus propios datos sin cambiar RFC ni nombre.
     * Verifica que updatedAt y updatedBy quedan asignados tras la actualización.
     */
    @Test
    @DisplayName("updateSupplier: debe actualizar el proveedor exitosamente")
    void shouldUpdateSupplierSuccessfully() {
        // ARRANGE — findByRfc y findByCompanyName retornan la MISMA entidad (mismo id)
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(supplierRepository.findByRfc("ABC123456789A")).thenReturn(Optional.of(entity));
        when(supplierRepository.findByCompanyName("Ferretería SA")).thenReturn(Optional.of(entity));
        when(supplierMapper.toDTO(entity)).thenReturn(outputDTO);

        // ACT
        SupplierDTO result = supplierService.updateSupplier(1L, inputDTO);

        // ASSERT
        assertNotNull(result);
        assertNotNull(entity.getUpdatedAt());
        assertEquals(user, entity.getUpdatedBy());
    }

    /**
     * Error Case: ID inexistente → falla antes de cualquier validación de negocio.
     */
    @Test
    @DisplayName("updateSupplier: debe lanzar excepción cuando el proveedor no existe")
    void shouldThrowWhenSupplierNotFoundOnUpdate() {
        // ARRANGE
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> supplierService.updateSupplier(99L, inputDTO));
    }

    /**
     * Error Case: el RFC nuevo pertenece a otro proveedor (id=2L).
     * La condición !existing.getId().equals(id) detecta el conflicto entre ids distintos.
     */
    @Test
    @DisplayName("updateSupplier: debe lanzar excepción cuando el RFC pertenece a otro proveedor")
    void shouldThrowWhenRfcBelongsToAnotherSupplier() {
        // ARRANGE — otro proveedor con el mismo RFC pero id distinto
        Supplier other = Supplier.builder().id(2L).rfc("ABC123456789A").build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(supplierRepository.findByRfc("ABC123456789A")).thenReturn(Optional.of(other));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> supplierService.updateSupplier(1L, inputDTO));
    }

    /**
     * Happy path: actualizar RFC al mismo valor que ya tiene el proveedor debe permitirse.
     * La condición !existing.getId().equals(id) garantiza que un proveedor puede
     * mantener su propio RFC sin que se lance la excepción de "RFC en uso por otro".
     */
    @Test
    @DisplayName("updateSupplier: debe permitir conservar el mismo RFC propio al actualizar")
    void shouldAllowKeepingOwnRfcOnUpdate() {
        // ARRANGE — findByRfc retorna el MISMO proveedor (mismo id=1L), no uno diferente
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(supplierRepository.findByRfc("ABC123456789A")).thenReturn(Optional.of(entity));
        when(supplierRepository.findByCompanyName("Ferretería SA")).thenReturn(Optional.of(entity));
        when(supplierMapper.toDTO(entity)).thenReturn(outputDTO);

        // ACT + ASSERT — no debe lanzar excepción al mantener el RFC propio
        // updateSupplier usa Hibernate dirty-checking (no llama a save() explícitamente),
        // por lo que solo se verifica que el método completa sin excepción y que
        // los campos de auditoría se asignan correctamente en la entidad.
        assertDoesNotThrow(() -> supplierService.updateSupplier(1L, inputDTO),
                "Un proveedor debe poder conservar su propio RFC al actualizarse");
        assertNotNull(entity.getUpdatedAt(), "updatedAt debe asignarse al actualizar");
        assertEquals(user, entity.getUpdatedBy(), "updatedBy debe ser el usuario autenticado");
    }

    /**
     * Error Case: la razón social nueva pertenece a otro proveedor (id=2L).
     */
    @Test
    @DisplayName("updateSupplier: debe lanzar excepción cuando la razón social pertenece a otro proveedor")
    void shouldThrowWhenCompanyNameBelongsToAnotherSupplier() {
        // ARRANGE
        Supplier other = Supplier.builder().id(2L).companyName("Ferretería SA").build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(supplierRepository.findByRfc("ABC123456789A")).thenReturn(Optional.of(entity));
        when(supplierRepository.findByCompanyName("Ferretería SA")).thenReturn(Optional.of(other));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> supplierService.updateSupplier(1L, inputDTO));
    }

    // =========================================================================
    // deactivateSupplier
    // =========================================================================

    /**
     * Happy Path: sin órdenes activas → active=false aplicado y updatedAt/updatedBy asignados.
     * Hibernate dirty-checking persistirá los cambios al cerrar la transacción.
     */
    @Test
    @DisplayName("deactivateSupplier: debe desactivar el proveedor cuando no tiene órdenes activas")
    void shouldDeactivateSupplierSuccessfully() {
        // ARRANGE
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(purchaseOrderRepository.findActiveOrdersBySupplier(1L))
                .thenReturn(Collections.emptyList());

        // ACT
        supplierService.deactivateSupplier(1L);

        // ASSERT
        assertFalse(entity.isActive());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(user, entity.getUpdatedBy());
        verify(purchaseOrderRepository, times(1)).findActiveOrdersBySupplier(1L);
    }

    /**
     * Error Case: el proveedor tiene órdenes PENDING o APPROVED activas.
     * El campo active NO debe modificarse cuando la validación falla —
     * el proveedor permanece activo hasta que sus órdenes sean resueltas.
     */
    @Test
    @DisplayName("deactivateSupplier: debe lanzar excepción cuando el proveedor tiene órdenes activas")
    void shouldThrowWhenSupplierHasActiveOrders() {
        // ARRANGE
        PurchaseOrder activeOrder = PurchaseOrder.builder()
                .id(1L).status(PurchaseOrderStatus.PENDING).build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(purchaseOrderRepository.findActiveOrdersBySupplier(1L))
                .thenReturn(List.of(activeOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> supplierService.deactivateSupplier(1L));

        // active NO debe haber cambiado
        assertTrue(entity.isActive());
    }
}
