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
import com.codigo2enter.almacenes.modules.sales.model.*;
import com.codigo2enter.almacenes.modules.sales.repository.ClientRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de repositorio para SaleOrderRepository.
 *
 * Verifica las queries JPQL que no pueden expresarse con query methods
 * derivados y que son difíciles de probar con mocks (el mock siempre
 * retorna lo que configuramos, no lo que la query real produce).
 *
 * Queries verificadas:
 *   - countByYear(): cuenta solo órdenes del año indicado, no de otros años.
 *     Riesgo: YEAR() es función específica de dialecto — si cambia el dialecto
 *     o la función, falla en runtime sin que ningún test lo detecte.
 *   - findActiveOrdersByClient(): solo retorna PENDING y APPROVED, excluye
 *     DELIVERED y CANCELLED. Usado para bloquear baja de clientes.
 *   - findByProductId(): atraviesa el JOIN con sale_order_details para
 *     encontrar órdenes que contienen un producto específico.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class SaleOrderRepositoryTest {

    @Autowired SaleOrderRepository saleOrderRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    private User testUser;
    private Client client;
    private Product product;
    private final long ts = System.currentTimeMillis() % 100000L;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("tester01").orElseGet(() -> {
            Role role = roleRepository.findByName("ROLE_WAREHOUSEMAN")
                    .orElseGet(() -> roleRepository.save(
                        Role.builder().name("ROLE_WAREHOUSEMAN").build()));
            return userRepository.save(User.builder()
                    .username("so_repo_test_" + ts)
                    .password("$2a$10$hash")
                    .email("so_repo_" + ts + "@test.com")
                    .roles(new HashSet<>(Set.of(role)))
                    .build());
        });

        Category category = categoryRepository.save(Category.builder()
                .name("Cat-SO-Repo-" + ts)
                .description("test")
                .createdBy(testUser)
                .build());

        Supplier supplier = supplierRepository.save(Supplier.builder()
                .rfc("SOR" + ts)
                .companyName("Prov SO Repo " + ts)
                .email("sor" + ts + "@test.com")
                .createdBy(testUser)
                .build());

        product = productRepository.save(Product.builder()
                .sku("SO-REPO-" + ts)
                .name("Prod SO Repo " + ts)
                .price(new BigDecimal("500.00"))
                .currentStock(100)
                .reservedStock(0)
                .minimumStock(5)
                .status("AVAILABLE")
                .active(true)
                .category(category)
                .supplier(supplier)
                .createdBy(testUser)
                .build());

        client = clientRepository.save(Client.builder()
                .name("Cliente SO Repo " + ts)
                .email("cliente_so_repo_" + ts + "@test.com")
                .createdBy(testUser)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // countByYear()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica que countByYear cuenta solo órdenes del año indicado.
     * Riesgo real: si YEAR() no funciona con el dialecto PostgreSQL, la función
     * retornaría 0 siempre y los números de orden generarían colisiones (OV-2026-0001
     * podría crearse múltiples veces). El do-while anti-colisión nunca se activaría
     * y el constraint UNIQUE de order_number lanzaría una excepción en producción.
     */
    @Test
    void countByYear_cuentaSoloOrdenesDelAnioIndicado() {
        int currentYear = LocalDateTime.now().getYear();

        long antes = saleOrderRepository.countByYear(currentYear);

        // Crear 2 órdenes en el año actual
        crearOrden("OV-REPO-Y1-" + ts);
        crearOrden("OV-REPO-Y2-" + ts);

        long despues = saleOrderRepository.countByYear(currentYear);

        assertEquals(antes + 2, despues,
            "countByYear debe contar exactamente las órdenes creadas en el año actual");
    }

    @Test
    void countByYear_noContaOrdenesDeOtroAnio() {
        int futureYear = LocalDateTime.now().getYear() + 5;

        long count = saleOrderRepository.countByYear(futureYear);

        assertEquals(0, count,
            "countByYear(" + futureYear + ") debe retornar 0 — no hay órdenes en ese año futuro");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findActiveOrdersByClient()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica que findActiveOrdersByClient incluye PENDING y APPROVED,
     * y excluye DELIVERED y CANCELLED.
     * Esta query protege la baja de clientes: si retornara resultados incorrectos,
     * podría bloquear la baja de un cliente que ya no tiene órdenes activas,
     * o permitir la baja de uno que sí las tiene.
     */
    @Test
    void findActiveOrdersByClient_incluyePendingYApproved_excluyeOtros() {
        SaleOrder pending   = crearOrden("OV-PEND-" + ts);
        SaleOrder approved  = crearOrdenConStatus("OV-APPR-" + ts, SaleOrderStatus.APPROVED);
        SaleOrder delivered = crearOrdenConStatus("OV-DELV-" + ts, SaleOrderStatus.DELIVERED);
        SaleOrder cancelled = crearOrdenConStatus("OV-CANC-" + ts, SaleOrderStatus.CANCELLED);

        List<SaleOrder> activas = saleOrderRepository.findActiveOrdersByClient(client.getId());
        List<Long> ids = activas.stream().map(SaleOrder::getId).toList();

        assertTrue(ids.contains(pending.getId()),
            "PENDING debe incluirse en órdenes activas del cliente");
        assertTrue(ids.contains(approved.getId()),
            "APPROVED debe incluirse en órdenes activas del cliente");
        assertFalse(ids.contains(delivered.getId()),
            "DELIVERED NO debe incluirse — ya fue entregada, no bloquea la baja");
        assertFalse(ids.contains(cancelled.getId()),
            "CANCELLED NO debe incluirse — ya fue cancelada, no bloquea la baja");
    }

    @Test
    void findActiveOrdersByClient_sinOrdenesActivas_retornaListaVacia() {
        // Solo crear órdenes terminadas
        crearOrdenConStatus("OV-ONLY-CANC-" + ts, SaleOrderStatus.CANCELLED);

        List<SaleOrder> activas = saleOrderRepository.findActiveOrdersByClient(client.getId());

        assertTrue(activas.isEmpty(),
            "findActiveOrdersByClient debe retornar lista vacía si todas las órdenes están terminadas");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByProductId()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica que findByProductId retorna órdenes que contienen el producto
     * en sus detalles, usando un JOIN a través de sale_order_details.
     * Riesgo: si el JOIN es incorrecto, este endpoint nunca retornaría resultados
     * y el operador no podría saber qué órdenes contienen un producto específico.
     */
    @Test
    void findByProductId_retornaOrdenesQueContienenElProducto() {
        SaleOrder ordenConProducto    = crearOrdenConDetalle("OV-WITH-PROD-" + ts);
        SaleOrder ordenSinProducto    = crearOrden("OV-WITHOUT-PROD-" + ts);

        List<SaleOrder> result = saleOrderRepository.findByProductId(product.getId());
        List<Long> ids = result.stream().map(SaleOrder::getId).toList();

        assertTrue(ids.contains(ordenConProducto.getId()),
            "findByProductId debe retornar órdenes que tienen el producto en sus detalles");
        assertFalse(ids.contains(ordenSinProducto.getId()),
            "findByProductId NO debe retornar órdenes que no tienen el producto");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private SaleOrder crearOrden(String orderNumber) {
        return crearOrdenConStatus(orderNumber, SaleOrderStatus.PENDING);
    }

    private SaleOrder crearOrdenConStatus(String orderNumber, SaleOrderStatus status) {
        return saleOrderRepository.save(SaleOrder.builder()
                .orderNumber(orderNumber)
                .status(status)
                .client(client)
                .createdBy(testUser)
                .totalAmount(BigDecimal.ZERO)
                .build());
    }

    private SaleOrder crearOrdenConDetalle(String orderNumber) {
        SaleOrder order = SaleOrder.builder()
                .orderNumber(orderNumber)
                .status(SaleOrderStatus.PENDING)
                .client(client)
                .createdBy(testUser)
                .totalAmount(new BigDecimal("500.00"))
                .build();

        SaleOrderDetail detail = SaleOrderDetail.builder()
                .quantity(1)
                .unitPrice(new BigDecimal("500.00"))
                .subtotal(new BigDecimal("500.00"))
                .product(product)
                .saleOrder(order)
                .build();

        order.getDetails().add(detail);
        return saleOrderRepository.save(order);
    }
}
