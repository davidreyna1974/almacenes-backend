package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.mapper.CategoryMapper;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.CategoryRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.core.exception.BusinessRuleException;
import com.codigo2enter.almacenes.core.exception.DuplicateResourceException;
import com.codigo2enter.almacenes.core.exception.ResourceNotFoundException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para CategoryServiceImpl.
 *
 * @ExtendWith(MockitoExtension.class) activa Mockito sin levantar el contexto
 * de Spring — cada test instancia solo la clase bajo prueba con dependencias
 * simuladas. Tiempo de ejecución: milisegundos por test.
 *
 * SecurityContextHolder se configura en @BeforeEach y se limpia en @AfterEach
 * para simular el usuario autenticado extraído del JWT en métodos de escritura.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository  productRepository;
    @Mock private UserRepository     userRepository;
    @Mock private CategoryMapper     categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private User        user;
    private CategoryDTO inputDTO;
    private Category    entity;
    private CategoryDTO outputDTO;

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

        // Stub para resolveAuthenticatedUser() en createCategory, updateCategory y deactivateCategory.
        lenient().when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));

        inputDTO = CategoryDTO.builder()
                .name("Herramientas")
                .description("Herramientas de trabajo")
                .active(true)
                .build();

        entity = Category.builder()
                .id(1L)
                .name("Herramientas")
                .description("Herramientas de trabajo")
                .active(true)
                .build();

        outputDTO = CategoryDTO.builder()
                .id(1L)
                .name("Herramientas")
                .description("Herramientas de trabajo")
                .active(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // createCategory
    // =========================================================================

    /**
     * Happy path: el nombre no existe → se crea la categoría correctamente.
     * Verifica que save() fue invocado exactamente una vez y que createdBy
     * queda asignado al usuario autenticado.
     */
    @Test
    @DisplayName("createCategory: debe crear la categoría cuando el nombre no existe")
    void shouldCreateCategorySuccessfully() {
        // ARRANGE
        when(categoryRepository.findByName("Herramientas")).thenReturn(Optional.empty());
        when(categoryMapper.toEntity(inputDTO)).thenReturn(entity);
        when(categoryRepository.save(entity)).thenReturn(entity);
        when(categoryMapper.toDTO(entity)).thenReturn(outputDTO);

        // ACT
        CategoryDTO result = categoryService.createCategory(inputDTO);

        // ASSERT
        assertNotNull(result);
        assertEquals("Herramientas", result.getName());
        assertEquals(user, entity.getCreatedBy());
        verify(categoryRepository, times(1)).save(entity);
    }

    /**
     * Error: el nombre ya está registrado en otra categoría.
     * save() nunca debe invocarse — la validación debe cortar el flujo de inmediato.
     */
    @Test
    @DisplayName("createCategory: debe lanzar DuplicateResourceException cuando el nombre ya existe")
    void shouldThrowWhenCategoryNameAlreadyExists() {
        // ARRANGE
        when(categoryRepository.findByName("Herramientas")).thenReturn(Optional.of(entity));

        // ACT + ASSERT
        assertThrows(DuplicateResourceException.class,
                () -> categoryService.createCategory(inputDTO));
        verify(categoryRepository, never()).save(any());
    }

    // =========================================================================
    // getAllActiveCategories
    // =========================================================================

    /**
     * Happy path: retorna la lista completa de categorías activas.
     */
    @Test
    @DisplayName("getAllActiveCategories: debe retornar todas las categorías activas")
    void shouldReturnAllActiveCategories() {
        // ARRANGE
        Category entity2 = Category.builder().id(2L).name("Electrónica").active(true).build();
        CategoryDTO outputDTO2 = CategoryDTO.builder().id(2L).name("Electrónica").active(true).build();

        when(categoryRepository.findByActiveTrue()).thenReturn(List.of(entity, entity2));
        when(categoryMapper.toDTOList(anyList())).thenReturn(List.of(outputDTO, outputDTO2));

        // ACT
        List<CategoryDTO> result = categoryService.getAllActiveCategories();

        // ASSERT
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(categoryMapper, times(1)).toDTOList(anyList());
    }

    // =========================================================================
    // updateCategory
    // =========================================================================

    /**
     * Happy path: la categoría existe y el nombre no pertenece a otra diferente.
     * Verifica que updatedAt y updatedBy quedan asignados tras la actualización.
     */
    @Test
    @DisplayName("updateCategory: debe actualizar la categoría exitosamente")
    void shouldUpdateCategorySuccessfully() {
        // ARRANGE — findByName retorna la MISMA entidad (mismo id) → no hay conflicto
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(categoryRepository.findByName("Herramientas")).thenReturn(Optional.of(entity));
        when(categoryMapper.toDTO(entity)).thenReturn(outputDTO);

        // ACT
        CategoryDTO result = categoryService.updateCategory(1L, inputDTO);

        // ASSERT
        assertNotNull(result);
        assertEquals("Herramientas", result.getName());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(user, entity.getUpdatedBy());
    }

    /**
     * Error: el id no corresponde a ninguna categoría en la base de datos.
     */
    @Test
    @DisplayName("updateCategory: debe lanzar ResourceNotFoundException cuando la categoría no existe")
    void shouldThrowWhenCategoryNotFoundOnUpdate() {
        // ARRANGE
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.updateCategory(99L, inputDTO));
    }

    /**
     * Error: el nuevo nombre ya está registrado en una categoría DIFERENTE (id=2L).
     */
    @Test
    @DisplayName("updateCategory: debe lanzar DuplicateResourceException cuando el nuevo nombre pertenece a otra categoría")
    void shouldThrowWhenNewNameBelongsToAnotherCategory() {
        // ARRANGE
        Category otherCategory = Category.builder().id(2L).name("Herramientas").active(true).build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(categoryRepository.findByName("Herramientas")).thenReturn(Optional.of(otherCategory));

        // ACT + ASSERT
        assertThrows(DuplicateResourceException.class,
                () -> categoryService.updateCategory(1L, inputDTO));
    }

    // =========================================================================
    // deactivateCategory
    // =========================================================================

    /**
     * Happy path: la categoría existe y no tiene productos activos asignados.
     * Verifica que active=false, updatedAt y updatedBy quedan asignados.
     */
    @Test
    @DisplayName("deactivateCategory: debe desactivar la categoría cuando no tiene productos activos")
    void shouldDeactivateCategorySuccessfully() {
        // ARRANGE
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(Collections.emptyList());

        // ACT
        categoryService.deactivateCategory(1L);

        // ASSERT
        assertFalse(entity.isActive());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(user, entity.getUpdatedBy());
    }

    /**
     * Error: el id no corresponde a ninguna categoría.
     */
    @Test
    @DisplayName("deactivateCategory: debe lanzar ResourceNotFoundException cuando la categoría no existe")
    void shouldThrowWhenCategoryNotFoundOnDeactivate() {
        // ARRANGE
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.deactivateCategory(99L));
        verify(productRepository, never()).findByCategoryIdAndActiveTrue(any());
    }

    /**
     * Error: la categoría tiene al menos un producto activo asignado.
     * La desactivación debe bloquearse para evitar dejar FKs huérfanas.
     */
    @Test
    @DisplayName("deactivateCategory: debe lanzar BusinessRuleException cuando la categoría tiene productos activos")
    void shouldThrowWhenCategoryHasActiveProducts() {
        // ARRANGE
        Product activeProduct = Product.builder().id(1L).sku("TOOL-001").active(true).build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(List.of(activeProduct));

        // ACT + ASSERT
        assertThrows(BusinessRuleException.class,
                () -> categoryService.deactivateCategory(1L));
        verify(categoryRepository, never()).save(any());
    }
}
