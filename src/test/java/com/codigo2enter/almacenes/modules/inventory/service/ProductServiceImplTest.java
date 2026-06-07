package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.modules.inventory.dto.ProductRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.ProductResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.mapper.ProductMapper;
import com.codigo2enter.almacenes.modules.inventory.mapper.StockMovementMapper;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.model.MovementType;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.model.StockMovement;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.CategoryRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.StockMovementRepository;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
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

import java.math.BigDecimal;
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
 * Pruebas unitarias para ProductServiceImpl.
 *
 * Cubre los 8 métodos públicos del servicio con 20 casos de prueba que
 * incluyen happy paths y todos los escenarios de error identificados:
 * SKU duplicado, categoría inexistente, stock insuficiente, tipo de
 * movimiento inválido y entidades no encontradas.
 *
 * Sin @SpringBootTest — Mockito instancia solo ProductServiceImpl con
 * dependencias simuladas. Ninguna consulta real llega a PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository       productRepository;
    @Mock private CategoryRepository      categoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository          userRepository;
    @Mock private SupplierRepository      supplierRepository;
    @Mock private ProductMapper           productMapper;
    @Mock private StockMovementMapper     stockMovementMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    // -------------------------------------------------------------------------
    // Datos reutilizables — reiniciados antes de cada test con @BeforeEach
    // -------------------------------------------------------------------------

    private Category           category;
    private Product            product;
    private User               user;
    private Supplier           supplier;
    private ProductRequestDTO  requestDTO;
    private ProductResponseDTO responseDTO;

    /**
     * Inicializa objetos con datos realistas antes de cada test.
     * product.currentStock = 50 es el punto de partida para todos los
     * tests de movimientos de stock.
     */
    @BeforeEach
    void setUp() {
        // SecurityContextHolder — lenient() porque los tests de solo lectura no lo usan
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("operador01");
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        user = User.builder()
                .id(1L).username("operador01").password("hashed").build();

        supplier = Supplier.builder()
                .id(1L).rfc("FERN123456").companyName("Ferretería SA").active(true).build();

        // Stubs para resolveAuthenticatedUser() y resolveSupplier() en métodos de escritura.
        lenient().when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));
        lenient().when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        category = Category.builder()
                .id(1L)
                .name("Herramientas")
                .active(true)
                .build();

        product = Product.builder()
                .id(1L)
                .sku("TOOL-001")
                .name("Taladro percutor")
                .currentStock(50)
                .minimumStock(10)
                .active(true)
                .category(category)
                .build();

        requestDTO = ProductRequestDTO.builder()
                .sku("TOOL-001")
                .name("Taladro percutor")
                .description("Taladro de alto rendimiento")
                .price(new BigDecimal("99.99"))
                .currentStock(10)
                .minimumStock(5)
                .status("AVAILABLE")
                .categoryId(1L)
                .supplierId(1L)
                .unitCost(new BigDecimal("50.00"))
                .build();

        responseDTO = ProductResponseDTO.builder()
                .id(1L)
                .sku("TOOL-001")
                .name("Taladro percutor")
                .categoryId(1L)
                .categoryName("Herramientas")
                .active(true)
                .build();
    }

    // =========================================================================
    // createProduct
    // =========================================================================

    /**
     * Happy path: SKU único y categoría existente.
     * Verifica que la categoría es asignada al producto antes de persistir
     * y que el DTO retornado contiene los datos esperados.
     */
    @Test
    @DisplayName("createProduct: debe crear el producto cuando SKU es único y categoría existe")
    void shouldCreateProductSuccessfully() {
        // ARRANGE
        when(productRepository.existsBySku("TOOL-001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productMapper.toEntity(requestDTO)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.toResponseDTO(product)).thenReturn(responseDTO);

        // ACT
        ProductResponseDTO result = productService.createProduct(requestDTO);

        // ASSERT
        assertNotNull(result);
        assertEquals("TOOL-001", result.getSku());
        assertEquals("Herramientas", result.getCategoryName());
        assertEquals(user, product.getCreatedBy());
        verify(productRepository, times(1)).save(product);
    }

    /**
     * Error: el SKU ya está registrado en otro producto.
     * save() nunca debe invocarse — la validación corta el flujo de inmediato.
     */
    @Test
    @DisplayName("createProduct: debe lanzar excepción cuando el SKU ya existe")
    void shouldThrowWhenSkuAlreadyExists() {
        // ARRANGE
        when(productRepository.existsBySku("TOOL-001")).thenReturn(true);

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.createProduct(requestDTO));

        verify(productRepository, never()).save(any());
    }

    /**
     * Error: el categoryId del DTO no corresponde a ninguna categoría en la BD.
     * La búsqueda del producto nunca debe persistirse si la categoría es inválida.
     */
    @Test
    @DisplayName("createProduct: debe lanzar excepción cuando la categoría no existe")
    void shouldThrowWhenCategoryNotFoundOnCreate() {
        // ARRANGE
        when(productRepository.existsBySku("TOOL-001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.createProduct(requestDTO));

        verify(productRepository, never()).save(any());
    }

    // =========================================================================
    // updateProduct
    // =========================================================================

    /**
     * Happy path: producto existe, el SKU no pertenece a otro producto diferente
     * y la categoría existe. Verifica que updateFromDTO y setCategory son aplicados.
     */
    @Test
    @DisplayName("updateProduct: debe actualizar el producto exitosamente")
    void shouldUpdateProductSuccessfully() {
        // ARRANGE — findBySku retorna el MISMO producto (mismo id=1L → sin conflicto)
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.findBySku("TOOL-001")).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.toResponseDTO(product)).thenReturn(responseDTO);

        // ACT
        ProductResponseDTO result = productService.updateProduct(1L, requestDTO);

        // ASSERT
        assertNotNull(result);
        assertNotNull(product.getUpdatedAt());
        assertEquals(user, product.getUpdatedBy());
        verify(productMapper, times(1)).updateFromDTO(requestDTO, product);
    }

    /**
     * Error: el id no corresponde a ningún producto en la base de datos.
     */
    @Test
    @DisplayName("updateProduct: debe lanzar excepción cuando el producto no existe")
    void shouldThrowWhenProductNotFoundOnUpdate() {
        // ARRANGE
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.updateProduct(99L, requestDTO));
    }

    /**
     * Error: el nuevo SKU ya está registrado en un producto DIFERENTE (id=2L).
     * Reproduce el caso donde el usuario cambia el SKU del producto 1
     * al SKU que ya tiene el producto 2.
     */
    @Test
    @DisplayName("updateProduct: debe lanzar excepción cuando el SKU pertenece a otro producto")
    void shouldThrowWhenSkuBelongsToAnotherProduct() {
        // ARRANGE — otro producto con el mismo SKU pero id distinto
        Product otherProduct = Product.builder()
                .id(2L)
                .sku("TOOL-001")
                .active(true)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.findBySku("TOOL-001")).thenReturn(Optional.of(otherProduct));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.updateProduct(1L, requestDTO));

        verify(productRepository, never()).save(any());
    }

    // =========================================================================
    // getLowStockProducts
    // =========================================================================

    /**
     * Happy path: la consulta JPQL retorna productos con stock crítico.
     * Verifica que el mapper de lista es invocado con el resultado del repositorio.
     */
    @Test
    @DisplayName("getLowStockProducts: debe retornar productos con stock bajo o igual al mínimo")
    void shouldReturnLowStockProducts() {
        // ARRANGE
        Product product2 = Product.builder().id(2L).sku("TOOL-002").currentStock(3)
                .minimumStock(5).active(true).build();
        ProductResponseDTO responseDTO2 = ProductResponseDTO.builder().id(2L).sku("TOOL-002").build();

        when(productRepository.findLowStockProducts()).thenReturn(List.of(product, product2));
        when(productMapper.toResponseDTOList(anyList())).thenReturn(List.of(responseDTO, responseDTO2));

        // ACT
        List<ProductResponseDTO> result = productService.getLowStockProducts();

        // ASSERT
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(productMapper, times(1)).toResponseDTOList(anyList());
    }

    // =========================================================================
    // registerStockMovement
    // =========================================================================

    /**
     * Happy path IN: movimiento de entrada de 10 unidades sobre stock=50.
     * El stock debe quedar en 60 y un StockMovement debe ser persistido.
     */
    @Test
    @DisplayName("registerStockMovement: debe incrementar el stock en movimiento IN")
    void shouldRegisterInMovementSuccessfully() {
        // ARRANGE
        StockMovementRequestDTO request = StockMovementRequestDTO.builder()
                .productId(1L).quantity(10).type("IN").reason("Compra orden #45")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));
        when(productRepository.save(product)).thenReturn(product);

        // ACT
        productService.registerStockMovement(request);

        // ASSERT — stock inicial 50 + 10 = 60
        assertEquals(60, product.getCurrentStock());
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    /**
     * Happy path OUT: movimiento de salida de 10 unidades sobre stock=50.
     * El stock debe quedar en 40 y un StockMovement debe ser persistido.
     */
    @Test
    @DisplayName("registerStockMovement: debe decrementar el stock en movimiento OUT")
    void shouldRegisterOutMovementSuccessfully() {
        // ARRANGE
        StockMovementRequestDTO request = StockMovementRequestDTO.builder()
                .productId(1L).quantity(10).type("OUT").reason("Venta")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));
        when(productRepository.save(product)).thenReturn(product);

        // ACT
        productService.registerStockMovement(request);

        // ASSERT — stock inicial 50 - 10 = 40
        assertEquals(40, product.getCurrentStock());
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    /**
     * Error: el campo type contiene "ENTRADA" en lugar de "IN" o "OUT".
     * La conversión String → enum falla antes de consultar la BD,
     * por lo que findById nunca debe invocarse.
     */
    @Test
    @DisplayName("registerStockMovement: debe lanzar excepción cuando el tipo de movimiento es inválido")
    void shouldThrowWhenMovementTypeIsInvalid() {
        // ARRANGE — tipo inválido, no coincide con ningún valor del enum MovementType
        StockMovementRequestDTO request = StockMovementRequestDTO.builder()
                .productId(1L).quantity(10).type("ENTRADA").reason("Compra")
                .build();

        // ACT + ASSERT — falla en la conversión, antes de buscar el producto
        assertThrows(RuntimeException.class,
                () -> productService.registerStockMovement(request));

        verify(productRepository, never()).findById(any());
        verify(stockMovementRepository, never()).save(any());
    }

    /**
     * Error: quantity = 0. El servicio incluye validación de defensa en profundidad
     * aunque el DTO ya tenga @Min(1), para proteger invocaciones directas al servicio
     * sin pasar por el @Valid del controlador.
     */
    @Test
    @DisplayName("registerStockMovement: debe lanzar excepción cuando la cantidad es cero o negativa")
    void shouldThrowWhenQuantityIsZeroOrNegative() {
        // ARRANGE — quantity=0 debe ser rechazado por el servicio
        StockMovementRequestDTO request = StockMovementRequestDTO.builder()
                .productId(1L).quantity(0).type("IN").reason("Ajuste")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.registerStockMovement(request));

        verify(stockMovementRepository, never()).save(any());
    }

    /**
     * Error: movimiento OUT de 100 unidades con stock=50.
     * El resultado sería -50, lo que no está permitido por regla de negocio.
     * El stock del producto no debe modificarse y no debe guardarse movimiento.
     */
    @Test
    @DisplayName("registerStockMovement: debe lanzar excepción cuando el stock es insuficiente para OUT")
    void shouldThrowWhenInsufficientStockForOutMovement() {
        // ARRANGE — solicitamos más unidades de las disponibles (100 > 50)
        StockMovementRequestDTO request = StockMovementRequestDTO.builder()
                .productId(1L).quantity(100).type("OUT").reason("Venta masiva")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.registerStockMovement(request));

        // El stock no debe haber cambiado y no debe guardarse ningún movimiento
        assertEquals(50, product.getCurrentStock());
        verify(productRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    // =========================================================================
    // searchProducts
    // =========================================================================

    /**
     * Sin parámetros (todos null) → retorna todos los productos activos paginados.
     * Verifica que el repositorio es invocado y el resultado se mapea correctamente.
     */
    @Test
    @DisplayName("searchProducts: sin filtros debe retornar todos los productos activos paginados")
    void shouldReturnAllProductsWhenNoFilters() {
        // ARRANGE
        org.springframework.data.domain.PageImpl<Product> productPage =
                new org.springframework.data.domain.PageImpl<>(List.of(product));
        when(productRepository.searchProducts(null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20,
                        org.springframework.data.domain.Sort.by("name").ascending())))
                .thenReturn(productPage);
        when(productMapper.toResponseDTO(product)).thenReturn(responseDTO);

        // ACT
        com.codigo2enter.almacenes.core.dto.PageResponseDTO<ProductResponseDTO> result =
                productService.searchProducts(null, null, null, null, 0, 20);

        // ASSERT
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(productRepository).searchProducts(null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20,
                        org.springframework.data.domain.Sort.by("name").ascending()));
    }

    /**
     * Con search="taladro" → el servicio normaliza el término y lo pasa al repositorio.
     */
    @Test
    @DisplayName("searchProducts: con término de búsqueda debe filtrar por sku y nombre")
    void shouldSearchByTermAndReturnMatchingProducts() {
        // ARRANGE
        org.springframework.data.domain.PageImpl<Product> productPage =
                new org.springframework.data.domain.PageImpl<>(List.of(product));
        when(productRepository.searchProducts(
                org.mockito.ArgumentMatchers.eq("taladro"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(productPage);
        when(productMapper.toResponseDTO(product)).thenReturn(responseDTO);

        // ACT
        com.codigo2enter.almacenes.core.dto.PageResponseDTO<ProductResponseDTO> result =
                productService.searchProducts("taladro", null, null, null, 0, 20);

        // ASSERT
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    /**
     * Search en blanco ("   ") → se normaliza a null para activar el IS NULL de JPQL.
     * Verifica que el servicio no pasa strings vacíos al repositorio.
     */
    @Test
    @DisplayName("searchProducts: término en blanco debe normalizarse a null")
    void shouldNormalizeBlankSearchToNull() {
        // ARRANGE
        org.springframework.data.domain.PageImpl<Product> productPage =
                new org.springframework.data.domain.PageImpl<>(List.of(product));
        when(productRepository.searchProducts(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(productPage);
        when(productMapper.toResponseDTO(product)).thenReturn(responseDTO);

        // ACT
        productService.searchProducts("   ", null, null, null, 0, 20);

        // ASSERT — el repositorio recibe null, no la cadena en blanco
        verify(productRepository).searchProducts(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
    }

    /**
     * Con todos los filtros combinados → el servicio los pasa íntegros al repositorio.
     */
    @Test
    @DisplayName("searchProducts: con todos los filtros debe pasarlos al repositorio")
    void shouldPassAllFiltersToRepository() {
        // ARRANGE
        org.springframework.data.domain.PageImpl<Product> productPage =
                new org.springframework.data.domain.PageImpl<>(List.of(product));
        when(productRepository.searchProducts(
                org.mockito.ArgumentMatchers.eq("drill"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("AVAILABLE"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(productPage);
        when(productMapper.toResponseDTO(product)).thenReturn(responseDTO);

        // ACT
        com.codigo2enter.almacenes.core.dto.PageResponseDTO<ProductResponseDTO> result =
                productService.searchProducts("drill", 1L, "AVAILABLE", 1L, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(productRepository).searchProducts(
                org.mockito.ArgumentMatchers.eq("drill"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("AVAILABLE"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any());
    }

    // =========================================================================
    // getBySku
    // =========================================================================

    /**
     * Happy path: existe un producto con el SKU buscado.
     */
    @Test
    @DisplayName("getBySku: debe retornar el producto cuando el SKU existe")
    void shouldReturnProductBySku() {
        // ARRANGE
        when(productRepository.findBySku("TOOL-001")).thenReturn(Optional.of(product));
        when(productMapper.toResponseDTO(product)).thenReturn(responseDTO);

        // ACT
        ProductResponseDTO result = productService.getBySku("TOOL-001");

        // ASSERT
        assertNotNull(result);
        assertEquals("TOOL-001", result.getSku());
    }

    /**
     * Error: no existe ningún producto con el SKU buscado.
     */
    @Test
    @DisplayName("getBySku: debe lanzar excepción cuando el SKU no existe")
    void shouldThrowWhenSkuNotFound() {
        // ARRANGE
        when(productRepository.findBySku("INEXISTENTE")).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.getBySku("INEXISTENTE"));
    }

    // =========================================================================
    // getByCategoryId
    // =========================================================================

    /**
     * Happy path: la categoría existe y tiene 2 productos activos asignados.
     */
    @Test
    @DisplayName("getByCategoryId: debe retornar productos activos de la categoría")
    void shouldReturnProductsByCategoryId() {
        // ARRANGE
        Product product2 = Product.builder().id(2L).sku("TOOL-002").active(true).build();
        ProductResponseDTO responseDTO2 = ProductResponseDTO.builder().id(2L).sku("TOOL-002").build();

        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(List.of(product, product2));
        when(productMapper.toResponseDTOList(anyList()))
                .thenReturn(List.of(responseDTO, responseDTO2));

        // ACT
        List<ProductResponseDTO> result = productService.getByCategoryId(1L);

        // ASSERT
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    /**
     * Error: el categoryId no corresponde a ninguna categoría.
     * Sin esta validación, la consulta devolvería [] silenciosamente —
     * el cliente no podría distinguir "categoría vacía" de "categoría inexistente".
     */
    @Test
    @DisplayName("getByCategoryId: debe lanzar excepción cuando la categoría no existe")
    void shouldThrowWhenCategoryNotFoundOnGetByCategoryId() {
        // ARRANGE
        when(categoryRepository.existsById(99L)).thenReturn(false);

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.getByCategoryId(99L));

        verify(productRepository, never()).findByCategoryIdAndActiveTrue(any());
    }

    // =========================================================================
    // deactivateProduct
    // =========================================================================

    /**
     * Happy path: el producto existe y se marca como inactivo (soft delete).
     * Verifica que setActive(false) fue aplicado sobre la entidad.
     */
    @Test
    @DisplayName("deactivateProduct: debe desactivar el producto exitosamente")
    void shouldDeactivateProductSuccessfully() {
        // ARRANGE
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // ACT
        productService.deactivateProduct(1L);

        // ASSERT — Hibernate dirty-checking persiste estos cambios al cerrar la transacción
        assertFalse(product.isActive());
        assertNotNull(product.getUpdatedAt());
        assertEquals(user, product.getUpdatedBy());
    }

    /**
     * Error: el id no corresponde a ningún producto existente.
     */
    @Test
    @DisplayName("deactivateProduct: debe lanzar excepción cuando el producto no existe")
    void shouldThrowWhenProductNotFoundOnDeactivate() {
        // ARRANGE
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.deactivateProduct(99L));
    }

    // =========================================================================
    // getStockMovementsByProduct
    // =========================================================================

    /**
     * Happy path: producto existente con 3 movimientos registrados.
     * El repositorio los devuelve ordenados por createdAt DESC — el servicio
     * no reordena, solo delega al mapper para la conversión.
     */
    @Test
    @DisplayName("getStockMovementsByProduct: debe retornar el historial de movimientos del producto")
    void shouldReturnStockMovementsForProduct() {
        // ARRANGE
        StockMovement mov1 = StockMovement.builder().id(1L).quantity(10).type(MovementType.IN).build();
        StockMovement mov2 = StockMovement.builder().id(2L).quantity(5).type(MovementType.OUT).build();
        StockMovement mov3 = StockMovement.builder().id(3L).quantity(20).type(MovementType.IN).build();

        StockMovementResponseDTO movDTO1 = StockMovementResponseDTO.builder().id(1L).type("IN").build();
        StockMovementResponseDTO movDTO2 = StockMovementResponseDTO.builder().id(2L).type("OUT").build();
        StockMovementResponseDTO movDTO3 = StockMovementResponseDTO.builder().id(3L).type("IN").build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.findByProductIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(mov1, mov2, mov3));
        when(stockMovementMapper.toResponseDTOList(anyList()))
                .thenReturn(List.of(movDTO1, movDTO2, movDTO3));

        // ACT
        List<StockMovementResponseDTO> result = productService.getStockMovementsByProduct(1L);

        // ASSERT
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(stockMovementMapper, times(1)).toResponseDTOList(anyList());
    }

    /**
     * Error: el productId no corresponde a ningún producto.
     * Sin esta validación, la consulta devolvería [] silenciosamente —
     * mismo problema que getByCategoryId con ID inexistente.
     */
    @Test
    @DisplayName("getStockMovementsByProduct: debe lanzar excepción cuando el producto no existe")
    void shouldThrowWhenProductNotFoundOnGetMovements() {
        // ARRANGE
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> productService.getStockMovementsByProduct(99L));

        verify(stockMovementRepository, never()).findByProductIdOrderByCreatedAtDesc(any());
    }
}
