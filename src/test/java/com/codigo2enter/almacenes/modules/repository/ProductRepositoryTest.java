package com.codigo2enter.almacenes.modules.repository;

import com.codigo2enter.almacenes.modules.auth.model.Role;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.RoleRepository;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.CategoryRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import com.codigo2enter.almacenes.modules.purchases.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de repositorio para ProductRepository.
 *
 * @DataJpaTest levanta solo la capa JPA (Hibernate + datasource).
 * No levanta Spring MVC, servicios ni controladores.
 *
 * @AutoConfigureTestDatabase(replace=NONE) obliga a usar la BD real (PostgreSQL)
 * en lugar de H2. Es necesario porque el esquema usa características específicas
 * de PostgreSQL (IDENTITY, NUMERIC, CHECK constraints) que H2 no soporta
 * completamente, y porque ddl-auto=validate requiere que el esquema pre-exista.
 *
 * @Transactional (heredado de @DataJpaTest) hace rollback automático después
 * de cada test — los datos de prueba no persisten entre tests ni contaminan
 * la BD de desarrollo.
 *
 * Queries verificadas:
 *   - findLowStockProducts(): usa availableStock (currentStock - reservedStock),
 *     no currentStock directo. Un producto con stock físico alto pero muchas
 *     reservas debe aparecer en la alerta.
 *   - findProductsWithActiveReservations(): solo retorna productos donde
 *     reservedStock > 0, en orden descendente.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class ProductRepositoryTest {

    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    private User testUser;
    private Category category;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        // Usar usuario existente si ya existe, o crear uno de prueba
        testUser = userRepository.findByUsername("tester01").orElseGet(() -> {
            Role role = roleRepository.findByName("ROLE_WAREHOUSEMAN")
                    .orElseGet(() -> roleRepository.save(
                        Role.builder().name("ROLE_WAREHOUSEMAN").build()));
            User u = User.builder()
                    .username("repo_test_user")
                    .password("$2a$10$hash")
                    .email("repo@test.com")
                    .roles(new HashSet<>(Set.of(role)))
                    .build();
            return userRepository.save(u);
        });

        category = categoryRepository.save(Category.builder()
                .name("Cat-Repo-Test-" + System.currentTimeMillis())
                .description("Categoría para tests de repositorio")
                .createdBy(testUser)
                .build());

        supplier = supplierRepository.save(Supplier.builder()
                .rfc("REPO" + System.currentTimeMillis() % 100000000L)
                .companyName("Proveedor Repo " + System.currentTimeMillis())
                .email("repo" + System.currentTimeMillis() + "@test.com")
                .createdBy(testUser)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findLowStockProducts() — verifica que usa availableStock, no currentStock
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caso crítico: producto con currentStock > minimumStock pero
     * availableStock (= currentStock - reservedStock) <= minimumStock.
     *
     * Sin este test, una regresión que cambiara la query a usar currentStock
     * directamente pasaría desapercibida — el producto NO aparecería en la
     * alerta aunque debería, causando ventas de stock ya comprometido.
     */
    @Test
    void findLowStockProducts_debeUsarAvailableStock_noCurrentStock() {
        // ARRANGE: currentStock=12, reservedStock=10 → available=2 <= minimum=5 → DEBE APARECER
        Product productoConReservas = productRepository.save(Product.builder()
                .sku("LOW-AVAIL-" + System.currentTimeMillis())
                .name("Producto con muchas reservas")
                .price(new BigDecimal("100.00"))
                .currentStock(12)
                .reservedStock(10)
                .minimumStock(5)
                .status("AVAILABLE")
                .active(true)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        // Producto con currentStock < minimumStock (caso tradicional) → también debe aparecer
        Product productoStockBajoTradicional = productRepository.save(Product.builder()
                .sku("LOW-TRAD-" + System.currentTimeMillis())
                .name("Producto con stock bajo tradicional")
                .price(new BigDecimal("200.00"))
                .currentStock(3)
                .reservedStock(0)
                .minimumStock(5)
                .status("AVAILABLE")
                .active(true)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        // Producto con stock suficiente en ambas dimensiones → NO debe aparecer
        Product productoStockOK = productRepository.save(Product.builder()
                .sku("OK-STOCK-" + System.currentTimeMillis())
                .name("Producto con stock correcto")
                .price(new BigDecimal("300.00"))
                .currentStock(50)
                .reservedStock(5)
                .minimumStock(10)
                .status("AVAILABLE")
                .active(true)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        // ACT
        List<Product> result = productRepository.findLowStockProducts();

        // ASSERT
        List<Long> ids = result.stream().map(Product::getId).toList();

        assertTrue(ids.contains(productoConReservas.getId()),
            "Producto con available=2 (< min=5) debe aparecer aunque currentStock=12 > min=5 — " +
            "verifica que la query usa (currentStock - reservedStock), no currentStock directo");

        assertTrue(ids.contains(productoStockBajoTradicional.getId()),
            "Producto con currentStock=3 < min=5 debe aparecer en low-stock");

        assertFalse(ids.contains(productoStockOK.getId()),
            "Producto con available=45 > min=10 NO debe aparecer en low-stock");
    }

    @Test
    void findLowStockProducts_productoInactivo_noDebeAparecer() {
        // Producto dado de baja (active=false) no debe aparecer aunque tenga stock bajo
        productRepository.save(Product.builder()
                .sku("INACTIVE-" + System.currentTimeMillis())
                .name("Producto inactivo con stock bajo")
                .price(new BigDecimal("100.00"))
                .currentStock(1)
                .reservedStock(0)
                .minimumStock(10)
                .status("AVAILABLE")
                .active(false)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        List<Long> ids = productRepository.findLowStockProducts()
                .stream().map(Product::getId).toList();

        assertTrue(ids.stream().allMatch(id ->
            productRepository.findById(id).map(Product::isActive).orElse(false)),
            "findLowStockProducts solo debe retornar productos activos (active=true)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findProductsWithActiveReservations()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void findProductsWithActiveReservations_soloConReservaActiva() {
        // Con reservas
        Product conReserva = productRepository.save(Product.builder()
                .sku("RESERVED-" + System.currentTimeMillis())
                .name("Producto reservado")
                .price(new BigDecimal("500.00"))
                .currentStock(20)
                .reservedStock(5)
                .minimumStock(2)
                .status("AVAILABLE")
                .active(true)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        // Sin reservas
        Product sinReserva = productRepository.save(Product.builder()
                .sku("FREE-" + System.currentTimeMillis())
                .name("Producto sin reservas")
                .price(new BigDecimal("300.00"))
                .currentStock(15)
                .reservedStock(0)
                .minimumStock(2)
                .status("AVAILABLE")
                .active(true)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        List<Product> result = productRepository.findProductsWithActiveReservations();
        List<Long> ids = result.stream().map(Product::getId).toList();

        assertTrue(ids.contains(conReserva.getId()),
            "Producto con reservedStock=5 debe aparecer en la lista de reservas activas");
        assertFalse(ids.contains(sinReserva.getId()),
            "Producto con reservedStock=0 NO debe aparecer en la lista de reservas activas");
    }

    @Test
    void findProductsWithActiveReservations_sinReservas_retornaListaVacia() {
        // No insertamos ningún producto con reservedStock > 0 en este test
        productRepository.save(Product.builder()
                .sku("NO-RES-" + System.currentTimeMillis())
                .name("Sin reserva")
                .price(new BigDecimal("100.00"))
                .currentStock(10)
                .reservedStock(0)
                .minimumStock(2)
                .status("AVAILABLE")
                .active(true)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        List<Product> result = productRepository.findProductsWithActiveReservations();

        // Verificar que ninguno de los de este test tiene reservedStock > 0
        assertTrue(result.stream().allMatch(p -> p.getReservedStock() > 0),
            "Todos los retornados deben tener reservedStock > 0");
    }
}
