package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.mapper.CategoryMapper;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.CategoryRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
 * Patrón AAA aplicado en cada test:
 *   ARRANGE → configurar comportamiento de los mocks (when...thenReturn)
 *   ACT     → invocar el método bajo prueba
 *   ASSERT  → verificar resultado e interacciones (assertEquals, verify)
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    /**
     * @Mock crea dobles de prueba — ninguna consulta real llega a la BD.
     * @InjectMocks crea la instancia real de CategoryServiceImpl e inyecta
     * automáticamente los @Mock declarados arriba en sus campos final.
     */
    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    // -------------------------------------------------------------------------
    // Datos reutilizables — se reinician antes de cada test con @BeforeEach
    // -------------------------------------------------------------------------

    private CategoryDTO inputDTO;
    private Category    entity;
    private CategoryDTO outputDTO;

    /**
     * @BeforeEach garantiza que cada test parte de datos limpios e independientes.
     * Así un test que modifique 'entity' (ej. setActive(false)) no contamina
     * los datos del test siguiente.
     */
    @BeforeEach
    void setUp() {
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

    // =========================================================================
    // createCategory
    // =========================================================================

    /**
     * Happy path: el nombre no existe → se crea la categoría correctamente.
     * Verifica que save() fue invocado exactamente una vez y que el DTO
     * retornado contiene el nombre esperado.
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
        verify(categoryRepository, times(1)).save(entity);
    }

    /**
     * Error: el nombre ya está registrado en otra categoría.
     * Verifica que se lanza RuntimeException y que save() nunca es invocado —
     * la validación debe cortar el flujo antes de intentar persistir.
     */
    @Test
    @DisplayName("createCategory: debe lanzar excepción cuando el nombre ya existe")
    void shouldThrowWhenCategoryNameAlreadyExists() {
        // ARRANGE — findByName retorna la entidad existente (Optional con valor)
        when(categoryRepository.findByName("Herramientas")).thenReturn(Optional.of(entity));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> categoryService.createCategory(inputDTO));

        // save() nunca debe invocarse si la validación falla
        verify(categoryRepository, never()).save(any());
    }

    // =========================================================================
    // getAllActiveCategories
    // =========================================================================

    /**
     * Happy path: retorna la lista completa de categorías activas.
     * Verifica que el mapper es invocado con la lista del repositorio y que
     * el tamaño de la respuesta coincide con el número de registros.
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
     * findByName retorna la misma entidad (mismo id=1L) → la condición
     * !existing.getId().equals(id) es false → no lanza excepción.
     */
    @Test
    @DisplayName("updateCategory: debe actualizar la categoría exitosamente")
    void shouldUpdateCategorySuccessfully() {
        // ARRANGE
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        // findByName retorna la MISMA entidad (mismo id) → no hay conflicto de nombre
        when(categoryRepository.findByName("Herramientas")).thenReturn(Optional.of(entity));
        when(categoryMapper.toDTO(entity)).thenReturn(outputDTO);

        // ACT
        CategoryDTO result = categoryService.updateCategory(1L, inputDTO);

        // ASSERT
        assertNotNull(result);
        assertEquals("Herramientas", result.getName());
    }

    /**
     * Error: el id no corresponde a ninguna categoría en la base de datos.
     * El servicio debe lanzar RuntimeException antes de intentar cualquier
     * operación de actualización.
     */
    @Test
    @DisplayName("updateCategory: debe lanzar excepción cuando la categoría no existe")
    void shouldThrowWhenCategoryNotFoundOnUpdate() {
        // ARRANGE — findById no encuentra el registro
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> categoryService.updateCategory(99L, inputDTO));
    }

    /**
     * Error: el nuevo nombre ya está registrado en una categoría DIFERENTE (id=2L).
     * La condición !existing.getId().equals(id) es true → lanza RuntimeException.
     * Reproduce el escenario donde el usuario intenta cambiar el nombre de la
     * categoría 1 a uno que ya tiene la categoría 2.
     */
    @Test
    @DisplayName("updateCategory: debe lanzar excepción cuando el nuevo nombre pertenece a otra categoría")
    void shouldThrowWhenNewNameBelongsToAnotherCategory() {
        // ARRANGE — otra categoría con el mismo nombre pero id distinto
        Category otherCategory = Category.builder()
                .id(2L)
                .name("Herramientas")
                .active(true)
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(categoryRepository.findByName("Herramientas")).thenReturn(Optional.of(otherCategory));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> categoryService.updateCategory(1L, inputDTO));
    }

    // =========================================================================
    // deactivateCategory
    // =========================================================================

    /**
     * Happy path: la categoría existe y no tiene productos activos asignados.
     * Verifica que setActive(false) fue aplicado sobre la entidad — Hibernate
     * detectará este cambio y ejecutará el UPDATE al cerrar la transacción.
     */
    @Test
    @DisplayName("deactivateCategory: debe desactivar la categoría cuando no tiene productos activos")
    void shouldDeactivateCategorySuccessfully() {
        // ARRANGE — lista vacía indica que no hay productos activos en esta categoría
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(Collections.emptyList());

        // ACT
        categoryService.deactivateCategory(1L);

        // ASSERT — entity.active debe ser false después de la operación
        assertFalse(entity.isActive());
    }

    /**
     * Error: el id no corresponde a ninguna categoría en la base de datos.
     * El servicio debe lanzar RuntimeException antes de consultar los productos.
     */
    @Test
    @DisplayName("deactivateCategory: debe lanzar excepción cuando la categoría no existe")
    void shouldThrowWhenCategoryNotFoundOnDeactivate() {
        // ARRANGE
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> categoryService.deactivateCategory(99L));

        // La consulta de productos nunca debe ejecutarse si la categoría no existe
        verify(productRepository, never()).findByCategoryIdAndActiveTrue(any());
    }

    /**
     * Error: la categoría tiene al menos un producto activo asignado.
     * La desactivación debe bloquearse para evitar dejar FKs huérfanas en el
     * frontend — el usuario debe reasignar o desactivar los productos primero.
     */
    @Test
    @DisplayName("deactivateCategory: debe lanzar excepción cuando la categoría tiene productos activos")
    void shouldThrowWhenCategoryHasActiveProducts() {
        // ARRANGE — simular un producto activo asignado a esta categoría
        Product activeProduct = Product.builder()
                .id(1L)
                .sku("TOOL-001")
                .active(true)
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(List.of(activeProduct));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> categoryService.deactivateCategory(1L));

        // La entidad no debe haber sido modificada
        verify(categoryRepository, never()).save(any());
    }
}
