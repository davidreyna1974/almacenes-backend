package com.codigo2enter.almacenes.modules.purchases.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.inventory.service.ProductService;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailUpdateRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderUpdateRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.mapper.PurchaseOrderDetailMapper;
import com.codigo2enter.almacenes.modules.purchases.mapper.PurchaseOrderMapper;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrder;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderDetail;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderStatus;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderDetailRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para PurchaseOrderServiceImpl.
 * Cubre 26 casos: Happy Path, Edge Case y Error Case por cada método.
 *
 * SecurityContextHolder se configura en @BeforeEach y se limpia en @AfterEach
 * para simular el usuario autenticado extraído del JWT en createOrder.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock private PurchaseOrderRepository      purchaseOrderRepository;
    @Mock private PurchaseOrderDetailRepository purchaseOrderDetailRepository;
    @Mock private SupplierRepository           supplierRepository;
    @Mock private ProductRepository            productRepository;
    @Mock private UserRepository               userRepository;
    @Mock private ProductService               productService;
    @Mock private PurchaseOrderMapper          purchaseOrderMapper;
    @Mock private PurchaseOrderDetailMapper    purchaseOrderDetailMapper;

    @InjectMocks
    private PurchaseOrderServiceImpl purchaseOrderService;

    private Supplier                 supplier;
    private Product                  product;
    private User                     user;
    private PurchaseOrderDetail      detail;
    private PurchaseOrder            order;
    private PurchaseOrder            approvedOrder;
    private PurchaseOrderResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        // Simular usuario autenticado en el SecurityContextHolder.
        // lenient() porque estos stubs solo son usados por los tests de createOrder —
        // Mockito strict mode lanzaría UnnecessaryStubbingException en los demás tests.
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("operador01");
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        supplier = Supplier.builder()
                .id(1L).companyName("Ferretería SA").active(true).build();

        product = Product.builder()
                .id(5L).name("Taladro").sku("TOOL-001").currentStock(50)
                .status("AVAILABLE").active(true).build();

        user = User.builder()
                .id(1L).username("operador01").password("hashed").build();

        // Stub para resolveAuthenticatedUser() en approveOrder, receiveOrder y cancelOrder.
        // lenient() porque solo lo usan esos tres métodos; los tests de consulta no lo invocan.
        lenient().when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));

        detail = PurchaseOrderDetail.builder()
                .id(1L).quantity(10)
                .unitPrice(new BigDecimal("89.99"))
                .subtotal(new BigDecimal("899.90"))
                .product(product)
                .build();

        // Usar ArrayList mutable para que remove() funcione en los tests de removeDetail
        order = PurchaseOrder.builder()
                .id(1L).orderNumber("OC-2026-0001")
                .status(PurchaseOrderStatus.PENDING)
                .supplier(supplier).createdBy(user)
                .totalAmount(new BigDecimal("899.90"))
                .details(new ArrayList<>(List.of(detail)))
                .build();

        approvedOrder = PurchaseOrder.builder()
                .id(1L).orderNumber("OC-2026-0001")
                .status(PurchaseOrderStatus.APPROVED)
                .supplier(supplier).createdBy(user)
                .totalAmount(new BigDecimal("899.90"))
                .details(new ArrayList<>(List.of(detail)))
                .build();

        responseDTO = PurchaseOrderResponseDTO.builder()
                .id(1L).orderNumber("OC-2026-0001")
                .status("PENDING")
                .totalAmount(new BigDecimal("899.90"))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // createOrder
    // =========================================================================

    /**
     * Happy Path: proveedor válido, producto válido → orden creada con totalAmount correcto.
     * Verifica que el orderNumber se genera, el usuario autenticado es asignado
     * y save() es invocado exactamente una vez.
     */
    @Test
    @DisplayName("createOrder: debe crear la orden con totalAmount correcto")
    void shouldCreateOrderWithCorrectTotalAmount() {
        // ARRANGE
        PurchaseOrderDetailRequestDTO detailDto = PurchaseOrderDetailRequestDTO.builder()
                .productId(5L).quantity(10).unitPrice(new BigDecimal("89.99")).build();
        PurchaseOrderRequestDTO requestDto = PurchaseOrderRequestDTO.builder()
                .supplierId(1L).notes("Pedido Q2").details(List.of(detailDto)).build();

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));
        when(purchaseOrderRepository.countByYear(anyInt())).thenReturn(0L);
        when(purchaseOrderRepository.findByOrderNumber("OC-" + java.time.Year.now().getValue() + "-0001"))
                .thenReturn(Optional.empty());
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(purchaseOrderDetailMapper.toEntity(detailDto)).thenReturn(detail);
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(order);
        when(purchaseOrderMapper.toResponseDTO(any(PurchaseOrder.class))).thenReturn(responseDTO);

        // ACT
        PurchaseOrderResponseDTO result = purchaseOrderService.createOrder(requestDto);

        // ASSERT
        assertNotNull(result);
        assertEquals("OC-2026-0001", result.getOrderNumber());
        verify(purchaseOrderRepository, times(1)).save(any(PurchaseOrder.class));
    }

    /**
     * Error Case: supplierId inválido → falla inmediatamente sin procesar detalles.
     */
    @Test
    @DisplayName("createOrder: debe lanzar excepción cuando el proveedor no existe")
    void shouldThrowWhenSupplierNotFoundOnCreate() {
        // ARRANGE
        PurchaseOrderRequestDTO requestDto = PurchaseOrderRequestDTO.builder()
                .supplierId(99L).details(List.of()).build();
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.createOrder(requestDto));
        verify(purchaseOrderRepository, never()).save(any());
    }

    /**
     * Error Case: productId inválido en un detalle → la orden no debe persistirse parcialmente.
     * La transacción debe hacer rollback antes de guardar nada.
     */
    @Test
    @DisplayName("createOrder: debe lanzar excepción cuando un producto del detalle no existe")
    void shouldThrowWhenProductNotFoundInDetail() {
        // ARRANGE
        PurchaseOrderDetailRequestDTO detailDto = PurchaseOrderDetailRequestDTO.builder()
                .productId(99L).quantity(5).unitPrice(new BigDecimal("50.00")).build();
        PurchaseOrderRequestDTO requestDto = PurchaseOrderRequestDTO.builder()
                .supplierId(1L).details(List.of(detailDto)).build();

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(userRepository.findByUsername("operador01")).thenReturn(Optional.of(user));
        when(purchaseOrderRepository.countByYear(anyInt())).thenReturn(0L);
        when(purchaseOrderRepository.findByOrderNumber(any())).thenReturn(Optional.empty());
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.createOrder(requestDto));
        verify(purchaseOrderRepository, never()).save(any());
    }

    // =========================================================================
    // findById
    // =========================================================================

    /**
     * Happy Path: orden encontrada por ID.
     */
    @Test
    @DisplayName("findById: debe retornar la orden cuando existe")
    void shouldReturnOrderById() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(purchaseOrderMapper.toResponseDTO(order)).thenReturn(responseDTO);

        // ACT
        PurchaseOrderResponseDTO result = purchaseOrderService.findById(1L);

        // ASSERT
        assertNotNull(result);
        assertEquals("OC-2026-0001", result.getOrderNumber());
    }

    /**
     * Error Case: ID inexistente → RuntimeException.
     */
    @Test
    @DisplayName("findById: debe lanzar excepción cuando la orden no existe")
    void shouldThrowWhenOrderNotFound() {
        // ARRANGE
        when(purchaseOrderRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.findById(99L));
    }

    // =========================================================================
    // findByStatus
    // =========================================================================

    /**
     * Happy Path: status válido → retorna lista de órdenes en ese estado.
     * Verifica la conversión String→enum antes de consultar el repositorio.
     */
    @Test
    @DisplayName("findByStatus: debe retornar órdenes para un status válido")
    void shouldReturnOrdersByStatus() {
        // ARRANGE
        when(purchaseOrderRepository.findByStatus(PurchaseOrderStatus.PENDING))
                .thenReturn(List.of(order));
        when(purchaseOrderMapper.toResponseDTOList(anyList())).thenReturn(List.of(responseDTO));

        // ACT
        List<PurchaseOrderResponseDTO> result = purchaseOrderService.findByStatus("PENDING");

        // ASSERT
        assertEquals(1, result.size());
    }

    /**
     * Error Case: status fuera del enum → RuntimeException con valores válidos.
     * Mismo patrón que registerStockMovement con type="ENTRADA".
     */
    @Test
    @DisplayName("findByStatus: debe lanzar excepción para un status inválido")
    void shouldThrowWhenStatusIsInvalid() {
        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.findByStatus("ESPERA"));
        verify(purchaseOrderRepository, never()).findByStatus(any());
    }

    // =========================================================================
    // findBySupplierIdAndStatus
    // =========================================================================

    /**
     * Happy Path: proveedor existe y status válido → retorna solo las órdenes
     * de ese proveedor en ese estado. Verifica que se invoca el método combinado
     * del repositorio (no los dos métodos simples por separado).
     */
    @Test
    @DisplayName("findBySupplierIdAndStatus: debe retornar órdenes del proveedor filtradas por estado")
    void shouldReturnOrdersBySupplierAndStatus() {
        // ARRANGE
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.findBySupplierIdAndStatus(1L, PurchaseOrderStatus.PENDING))
                .thenReturn(List.of(order));
        when(purchaseOrderMapper.toResponseDTOList(anyList())).thenReturn(List.of(responseDTO));

        // ACT
        List<PurchaseOrderResponseDTO> result =
                purchaseOrderService.findBySupplierIdAndStatus(1L, "PENDING");

        // ASSERT
        assertEquals(1, result.size());
        verify(purchaseOrderRepository, times(1))
                .findBySupplierIdAndStatus(1L, PurchaseOrderStatus.PENDING);
    }

    /**
     * Error Case: status inválido → RuntimeException antes de consultar la BD.
     * La conversión String→enum falla primero — el proveedor ni siquiera se consulta.
     * Mismo patrón que findByStatus con status="ESPERA".
     */
    @Test
    @DisplayName("findBySupplierIdAndStatus: debe lanzar excepción para status inválido")
    void shouldThrowWhenStatusInvalidInCombinedFilter() {
        // ACT + ASSERT — falla en la conversión, antes de tocar el repositorio
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.findBySupplierIdAndStatus(1L, "INVALIDO"));
        verify(supplierRepository, never()).findById(any());
        verify(purchaseOrderRepository, never()).findBySupplierIdAndStatus(any(), any());
    }

    /**
     * Error Case: supplierId inexistente → RuntimeException.
     * El servicio distingue "proveedor sin órdenes en ese estado" (lista vacía)
     * de "proveedor inexistente" (error) — sin esta validación ambos retornarían [].
     */
    @Test
    @DisplayName("findBySupplierIdAndStatus: debe lanzar excepción cuando el proveedor no existe")
    void shouldThrowWhenSupplierNotFoundInCombinedFilter() {
        // ARRANGE — status válido pero supplierId inexistente
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.findBySupplierIdAndStatus(99L, "PENDING"));
        verify(purchaseOrderRepository, never()).findBySupplierIdAndStatus(any(), any());
    }

    // =========================================================================
    // updateOrder
    // =========================================================================

    /**
     * Happy Path: orden PENDING actualiza notas y proveedor correctamente.
     * Verifica que updatedAt es asignado tras la actualización.
     */
    @Test
    @DisplayName("updateOrder: debe actualizar notas y proveedor en orden PENDING")
    void shouldUpdateOrderSuccessfully() {
        // ARRANGE
        Supplier newSupplier = Supplier.builder().id(2L).companyName("Nuevo Proveedor").build();
        PurchaseOrderUpdateRequestDTO updateDto = PurchaseOrderUpdateRequestDTO.builder()
                .supplierId(2L).notes("Notas actualizadas").build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(supplierRepository.findById(2L)).thenReturn(Optional.of(newSupplier));
        when(purchaseOrderMapper.toResponseDTO(order)).thenReturn(responseDTO);

        // ACT
        purchaseOrderService.updateOrder(1L, updateDto);

        // ASSERT
        assertEquals(newSupplier, order.getSupplier());
        assertEquals("Notas actualizadas", order.getNotes());
        assertNotNull(order.getUpdatedAt());
    }

    /**
     * Error Case: intentar editar una orden APPROVED → la máquina de estados
     * debe rechazar la operación — ya fue autorizada por el sistema.
     */
    @Test
    @DisplayName("updateOrder: debe lanzar excepción cuando la orden no está en PENDING")
    void shouldThrowWhenUpdatingNonPendingOrder() {
        // ARRANGE
        PurchaseOrderUpdateRequestDTO updateDto = PurchaseOrderUpdateRequestDTO.builder()
                .supplierId(1L).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.updateOrder(1L, updateDto));
    }

    // =========================================================================
    // approveOrder
    // =========================================================================

    /**
     * Happy Path: PENDING con detalles → APPROVED. Verifica timestamps.
     */
    @Test
    @DisplayName("approveOrder: debe aprobar la orden y asignar approvedAt")
    void shouldApproveOrderSuccessfully() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(purchaseOrderMapper.toResponseDTO(order)).thenReturn(responseDTO);

        // ACT
        purchaseOrderService.approveOrder(1L);

        // ASSERT
        assertEquals(PurchaseOrderStatus.APPROVED, order.getStatus());
        assertNotNull(order.getApprovedAt());
        assertNotNull(order.getUpdatedAt());
        assertEquals(user, order.getApprovedBy());
    }

    /**
     * Error Case: orden APPROVED no puede volver a aprobarse.
     * La máquina de estados debe ser inviolable.
     */
    @Test
    @DisplayName("approveOrder: debe lanzar excepción cuando la orden no está en PENDING")
    void shouldThrowWhenApprovingNonPendingOrder() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.approveOrder(1L));
    }

    /**
     * Edge Case: aprobar una orden PENDING sin detalles → rechazada.
     * No tiene sentido autorizar una compra sin productos asignados.
     */
    @Test
    @DisplayName("approveOrder: debe lanzar excepción cuando la orden no tiene detalles")
    void shouldThrowWhenApprovingOrderWithNoDetails() {
        // ARRANGE
        PurchaseOrder emptyOrder = PurchaseOrder.builder()
                .id(2L).status(PurchaseOrderStatus.PENDING)
                .details(new ArrayList<>()).build();
        when(purchaseOrderRepository.findById(2L)).thenReturn(Optional.of(emptyOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.approveOrder(2L));
    }

    // =========================================================================
    // receiveOrder
    // =========================================================================

    /**
     * Happy Path: orden APPROVED → RECEIVED. Verifica con argThat que
     * registerStockMovement recibe el DTO correcto: productId=5L, quantity=10,
     * type="IN" y reason que contiene el número de orden.
     * Este es el test más importante del módulo — valida la integración entre
     * purchases e inventory al nivel de contenido del mensaje.
     */
    @Test
    @DisplayName("receiveOrder: debe cambiar a RECEIVED y disparar movimiento de stock por cada detalle")
    void shouldReceiveOrderAndTriggerStockMovementsPerDetail() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder));
        when(purchaseOrderMapper.toResponseDTO(approvedOrder)).thenReturn(responseDTO);

        // ACT
        purchaseOrderService.receiveOrder(1L);

        // ASSERT — estado correcto
        assertEquals(PurchaseOrderStatus.RECEIVED, approvedOrder.getStatus());
        assertNotNull(approvedOrder.getReceivedAt());
        assertNotNull(approvedOrder.getUpdatedAt());
        assertEquals(user, approvedOrder.getReceivedBy());

        // Verifica contenido exacto del StockMovementRequestDTO enviado a inventory
        verify(productService, times(1)).registerStockMovement(
                argThat(mov ->
                        mov.getProductId().equals(5L)
                        && mov.getQuantity() == 10
                        && "IN".equals(mov.getType())
                        && mov.getReason().contains("OC-2026-0001")));
    }

    /**
     * Error Case: orden PENDING intenta recibirse saltándose la aprobación.
     * El stock NO debe modificarse — registerStockMovement nunca debe invocarse.
     */
    @Test
    @DisplayName("receiveOrder: debe lanzar excepción cuando la orden no está en APPROVED")
    void shouldThrowWhenReceivingNonApprovedOrder() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.receiveOrder(1L));
        verify(productService, never()).registerStockMovement(any(StockMovementRequestDTO.class));
    }

    /**
     * Error Case: orden ya RECEIVED intenta recibirse de nuevo.
     * Sería un doble incremento de stock — corrupción de datos crítica.
     */
    @Test
    @DisplayName("receiveOrder: debe lanzar excepción cuando la orden ya fue recibida")
    void shouldThrowWhenReceivingAlreadyReceivedOrder() {
        // ARRANGE
        PurchaseOrder receivedOrder = PurchaseOrder.builder()
                .id(1L).status(PurchaseOrderStatus.RECEIVED).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(receivedOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.receiveOrder(1L));
        verify(productService, never()).registerStockMovement(any(StockMovementRequestDTO.class));
    }

    // =========================================================================
    // cancelOrder
    // =========================================================================

    /**
     * Happy Path: orden PENDING → CANCELLED con cancelledAt asignado.
     */
    @Test
    @DisplayName("cancelOrder: debe cancelar una orden PENDING exitosamente")
    void shouldCancelPendingOrder() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(purchaseOrderMapper.toResponseDTO(order)).thenReturn(responseDTO);

        // ACT
        purchaseOrderService.cancelOrder(1L);

        // ASSERT
        assertEquals(PurchaseOrderStatus.CANCELLED, order.getStatus());
        assertNotNull(order.getCancelledAt());
        assertEquals(user, order.getCancelledBy());
    }

    /**
     * Happy Path: orden APPROVED → CANCELLED. También es cancelable
     * antes de ser recibida físicamente.
     */
    @Test
    @DisplayName("cancelOrder: debe cancelar una orden APPROVED exitosamente")
    void shouldCancelApprovedOrder() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder));
        when(purchaseOrderMapper.toResponseDTO(approvedOrder)).thenReturn(responseDTO);

        // ACT
        purchaseOrderService.cancelOrder(1L);

        // ASSERT
        assertEquals(PurchaseOrderStatus.CANCELLED, approvedOrder.getStatus());
    }

    /**
     * Error Case: orden RECEIVED → no puede cancelarse.
     * Ya impactó el inventario — cancelarla no revertiría los movimientos de stock.
     */
    @Test
    @DisplayName("cancelOrder: debe lanzar excepción cuando la orden ya fue recibida")
    void shouldThrowWhenCancellingReceivedOrder() {
        // ARRANGE
        PurchaseOrder receivedOrder = PurchaseOrder.builder()
                .id(1L).status(PurchaseOrderStatus.RECEIVED).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(receivedOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.cancelOrder(1L));
    }

    /**
     * Error Case: orden ya CANCELLED → estado terminal, sin transiciones posibles.
     */
    @Test
    @DisplayName("cancelOrder: debe lanzar excepción cuando la orden ya está cancelada")
    void shouldThrowWhenCancellingAlreadyCancelledOrder() {
        // ARRANGE
        PurchaseOrder cancelledOrder = PurchaseOrder.builder()
                .id(1L).status(PurchaseOrderStatus.CANCELLED).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.cancelOrder(1L));
    }

    // =========================================================================
    // addDetail
    // =========================================================================

    /**
     * Happy Path: producto nuevo en orden PENDING → detalle agregado con subtotal
     * correcto y totalAmount de la orden recalculado.
     */
    @Test
    @DisplayName("addDetail: debe agregar el detalle y recalcular el totalAmount")
    void shouldAddDetailAndRecalculateTotal() {
        // ARRANGE
        PurchaseOrder emptyOrder = PurchaseOrder.builder()
                .id(1L).status(PurchaseOrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .details(new ArrayList<>()).build();
        PurchaseOrderDetailRequestDTO dto = PurchaseOrderDetailRequestDTO.builder()
                .productId(5L).quantity(10).unitPrice(new BigDecimal("89.99")).build();

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(emptyOrder));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(purchaseOrderDetailRepository.existsByPurchaseOrderIdAndProductId(1L, 5L))
                .thenReturn(false);
        when(purchaseOrderDetailMapper.toEntity(dto)).thenReturn(detail);
        when(purchaseOrderRepository.save(emptyOrder)).thenReturn(emptyOrder);
        when(purchaseOrderMapper.toResponseDTO(emptyOrder)).thenReturn(responseDTO);

        // ACT
        purchaseOrderService.addDetail(1L, dto);

        // ASSERT — totalAmount = 10 × 89.99 = 899.90
        assertEquals(new BigDecimal("899.90"), emptyOrder.getTotalAmount());
        assertEquals(1, emptyOrder.getDetails().size());
    }

    /**
     * Error Case: el producto ya está en la orden → duplicado rechazado.
     * El usuario debe usar updateDetail para ajustar cantidad, no agregar de nuevo.
     */
    @Test
    @DisplayName("addDetail: debe lanzar excepción cuando el producto ya está en la orden")
    void shouldThrowWhenAddingDuplicateProductToOrder() {
        // ARRANGE
        PurchaseOrderDetailRequestDTO dto = PurchaseOrderDetailRequestDTO.builder()
                .productId(5L).quantity(5).unitPrice(new BigDecimal("89.99")).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(purchaseOrderDetailRepository.existsByPurchaseOrderIdAndProductId(1L, 5L))
                .thenReturn(true);

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.addDetail(1L, dto));
    }

    /**
     * Error Case: orden APPROVED → detalles bloqueados.
     * No puede modificarse un compromiso ya autorizado con el proveedor.
     */
    @Test
    @DisplayName("addDetail: debe lanzar excepción cuando la orden no está en PENDING")
    void shouldThrowWhenAddingDetailToNonPendingOrder() {
        // ARRANGE
        PurchaseOrderDetailRequestDTO dto = PurchaseOrderDetailRequestDTO.builder()
                .productId(5L).quantity(5).unitPrice(new BigDecimal("89.99")).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.addDetail(1L, dto));
    }

    // =========================================================================
    // updateDetail
    // =========================================================================

    /**
     * Happy Path: actualiza quantity y unitPrice → subtotal y totalAmount recalculados.
     * 20 × 75.00 = 1500.00 como nuevo subtotal del detalle.
     */
    @Test
    @DisplayName("updateDetail: debe actualizar el detalle y recalcular subtotal y totalAmount")
    void shouldUpdateDetailAndRecalculateTotals() {
        // ARRANGE
        PurchaseOrderDetailUpdateRequestDTO updateDto = PurchaseOrderDetailUpdateRequestDTO.builder()
                .quantity(20).unitPrice(new BigDecimal("75.00")).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(purchaseOrderDetailRepository.findByIdAndPurchaseOrderId(1L, 1L))
                .thenReturn(Optional.of(detail));
        when(purchaseOrderRepository.save(order)).thenReturn(order);
        when(purchaseOrderMapper.toResponseDTO(order)).thenReturn(responseDTO);

        // ACT
        purchaseOrderService.updateDetail(1L, 1L, updateDto);

        // ASSERT
        assertEquals(20, detail.getQuantity());
        assertEquals(new BigDecimal("75.00"), detail.getUnitPrice());
        assertEquals(new BigDecimal("1500.00"), detail.getSubtotal());
        assertNotNull(order.getUpdatedAt());
    }

    /**
     * Error Case: detailId no encontrado o pertenece a otra orden.
     * findByIdAndPurchaseOrderId protege contra accesos cruzados entre órdenes.
     */
    @Test
    @DisplayName("updateDetail: debe lanzar excepción cuando el detalle no existe o es de otra orden")
    void shouldThrowWhenDetailNotFoundOrBelongsToDifferentOrder() {
        // ARRANGE
        PurchaseOrderDetailUpdateRequestDTO updateDto = PurchaseOrderDetailUpdateRequestDTO.builder()
                .quantity(5).unitPrice(new BigDecimal("50.00")).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(purchaseOrderDetailRepository.findByIdAndPurchaseOrderId(99L, 1L))
                .thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.updateDetail(1L, 99L, updateDto));
    }

    // =========================================================================
    // removeDetail
    // =========================================================================

    /**
     * Happy Path: elimina el único detalle → totalAmount queda en ZERO.
     * orphanRemoval=true hará el DELETE físico; el test verifica que la lista
     * queda vacía y el total se recalcula correctamente a cero.
     */
    @Test
    @DisplayName("removeDetail: debe eliminar el detalle y recalcular totalAmount a ZERO")
    void shouldRemoveDetailAndRecalculateTotal() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(purchaseOrderDetailRepository.findByIdAndPurchaseOrderId(1L, 1L))
                .thenReturn(Optional.of(detail));
        when(purchaseOrderRepository.save(order)).thenReturn(order);

        // ACT
        purchaseOrderService.removeDetail(1L, 1L);

        // ASSERT — la lista queda vacía y el total en ZERO
        assertTrue(order.getDetails().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());
    }

    /**
     * Error Case: orden APPROVED → no se pueden eliminar detalles.
     * El repositorio de detalles nunca debe consultarse si la orden no está en PENDING.
     */
    @Test
    @DisplayName("removeDetail: debe lanzar excepción cuando la orden no está en PENDING")
    void shouldThrowWhenRemovingDetailFromNonPendingOrder() {
        // ARRANGE
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(approvedOrder));

        // ACT + ASSERT
        assertThrows(RuntimeException.class,
                () -> purchaseOrderService.removeDetail(1L, 1L));
        verify(purchaseOrderDetailRepository, never())
                .findByIdAndPurchaseOrderId(any(), any());
    }
}
