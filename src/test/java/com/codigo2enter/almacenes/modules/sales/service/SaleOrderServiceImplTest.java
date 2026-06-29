package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.inventory.service.ProductService;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.mapper.SaleOrderDetailMapper;
import com.codigo2enter.almacenes.modules.sales.mapper.SaleOrderMapper;
import com.codigo2enter.almacenes.modules.sales.model.*;
import com.codigo2enter.almacenes.modules.sales.repository.ClientRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderDetailRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleOrderServiceImplTest {

    @Mock SaleOrderRepository saleOrderRepository;
    @Mock SaleOrderDetailRepository saleOrderDetailRepository;
    @Mock ClientRepository clientRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductService productService;
    @Mock UserRepository userRepository;
    @Mock SaleOrderMapper saleOrderMapper;
    @Mock SaleOrderDetailMapper saleOrderDetailMapper;
    @InjectMocks SaleOrderServiceImpl saleOrderService;

    private User user;
    private Client client;
    private Product product;
    private SaleOrder order;
    private SaleOrderDetail detail;
    private SaleOrderResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("tester01");
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        user = User.builder().id(1L).username("tester01").password("hash").build();
        lenient().when(userRepository.findByUsername("tester01")).thenReturn(Optional.of(user));

        client = Client.builder().id(1L).name("Comercial Reyes SA").active(true).build();
        lenient().when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        lenient().when(clientRepository.existsById(1L)).thenReturn(true);

        product = Product.builder()
                .id(10L).name("Taladro").sku("TAL-001").active(true)
                .currentStock(50).reservedStock(0).minimumStock(5)
                .price(new BigDecimal("1500.00"))
                .unitCost(new BigDecimal("1000.00"))
                .version(0L)
                .build();
        lenient().when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        detail = SaleOrderDetail.builder()
                .id(1L).quantity(5)
                .unitPrice(new BigDecimal("1500.00"))
                .unitCost(new BigDecimal("1000.00"))
                .subtotal(new BigDecimal("7500.00"))
                .product(product)
                .build();

        order = SaleOrder.builder()
                .id(1L).orderNumber("OV-2026-0001")
                .status(SaleOrderStatus.PENDING)
                .client(client).createdBy(user)
                .totalAmount(new BigDecimal("7500.00"))
                .details(new ArrayList<>(List.of(detail)))
                .build();
        detail.setSaleOrder(order);

        responseDTO = new SaleOrderResponseDTO();
        lenient().when(saleOrderMapper.toResponseDTO(any())).thenReturn(responseDTO);
        lenient().when(saleOrderRepository.countByYear(anyInt())).thenReturn(0L);
        lenient().when(saleOrderRepository.findByOrderNumber(any())).thenReturn(Optional.empty());
        lenient().when(saleOrderRepository.save(any())).thenReturn(order);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── createOrder ──────────────────────────────────────────────────────────

    @Test
    void createOrder_unitCostCapturado() {
        SaleOrderDetailRequestDTO detailDto = SaleOrderDetailRequestDTO.builder()
                .productId(10L).quantity(5).unitPrice(new BigDecimal("1500.00")).build();
        SaleOrderRequestDTO dto = SaleOrderRequestDTO.builder()
                .clientId(1L).details(List.of(detailDto)).build();

        SaleOrderDetail detailFromMapper = SaleOrderDetail.builder()
                .quantity(5).unitPrice(new BigDecimal("1500.00")).build();
        when(saleOrderDetailMapper.toEntity(detailDto)).thenReturn(detailFromMapper);

        saleOrderService.createOrder(dto);

        assertEquals(new BigDecimal("1000.00"), detailFromMapper.getUnitCost());
        assertEquals(new BigDecimal("7500.00"), detailFromMapper.getSubtotal());
        verify(saleOrderRepository).save(any());
    }

    @Test
    void createOrder_unitCostNullCuandoProductoSinCosto() {
        product.setUnitCost(null);
        SaleOrderDetailRequestDTO detailDto = SaleOrderDetailRequestDTO.builder()
                .productId(10L).quantity(2).unitPrice(new BigDecimal("100.00")).build();
        SaleOrderRequestDTO dto = SaleOrderRequestDTO.builder()
                .clientId(1L).details(List.of(detailDto)).build();

        SaleOrderDetail detailFromMapper = SaleOrderDetail.builder()
                .quantity(2).unitPrice(new BigDecimal("100.00")).build();
        when(saleOrderDetailMapper.toEntity(detailDto)).thenReturn(detailFromMapper);

        assertDoesNotThrow(() -> saleOrderService.createOrder(dto));
        assertNull(detailFromMapper.getUnitCost());
    }

    @Test
    void createOrder_clienteNoExiste_debeLanzarExcepcion() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());
        SaleOrderRequestDTO dto = SaleOrderRequestDTO.builder()
                .clientId(99L)
                .details(List.of(SaleOrderDetailRequestDTO.builder()
                        .productId(10L).quantity(1).unitPrice(BigDecimal.ONE).build()))
                .build();

        assertThrows(RuntimeException.class, () -> saleOrderService.createOrder(dto));
        verify(saleOrderRepository, never()).save(any());
    }

    @Test
    void createOrder_productoInactivo_debeLanzarExcepcion() {
        product.setActive(false);
        SaleOrderRequestDTO dto = SaleOrderRequestDTO.builder()
                .clientId(1L)
                .details(List.of(SaleOrderDetailRequestDTO.builder()
                        .productId(10L).quantity(1).unitPrice(BigDecimal.ONE).build()))
                .build();

        assertThrows(RuntimeException.class, () -> saleOrderService.createOrder(dto));
        verify(saleOrderRepository, never()).save(any());
    }

    // ── approveOrder ─────────────────────────────────────────────────────────

    @Test
    void approveOrder_stockSuficiente_debeReservar() {
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.saveAndFlush(any())).thenReturn(product);

        saleOrderService.approveOrder(1L);

        assertEquals(5, product.getReservedStock());
        assertEquals(SaleOrderStatus.APPROVED, order.getStatus());
        assertNotNull(order.getApprovedAt());
        assertNotNull(order.getApprovedBy());
    }

    @Test
    void approveOrder_stockInsuficiente_noDebeReservar() {
        product.setCurrentStock(2);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class, () -> saleOrderService.approveOrder(1L));
        assertEquals(0, product.getReservedStock());
        assertEquals(SaleOrderStatus.PENDING, order.getStatus());
        verify(productRepository, never()).save(any());
    }

    @Test
    void approveOrder_ordenYaAprobada_debeLanzarExcepcion() {
        order.setStatus(SaleOrderStatus.APPROVED);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class, () -> saleOrderService.approveOrder(1L));
    }

    @Test
    void approveOrder_sinDetalles_debeLanzarExcepcion() {
        order.getDetails().clear();
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class, () -> saleOrderService.approveOrder(1L));
    }

    // ── deliverOrder ─────────────────────────────────────────────────────────

    @Test
    void deliverOrder_stockFisicoCorrecto_debeEntregarYGenerarMovimiento() {
        order.setStatus(SaleOrderStatus.APPROVED);
        product.setCurrentStock(50);
        product.setReservedStock(5);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.save(any())).thenReturn(product);

        saleOrderService.deliverOrder(1L);

        assertEquals(0, product.getReservedStock());
        assertEquals(SaleOrderStatus.DELIVERED, order.getStatus());

        ArgumentCaptor<StockMovementRequestDTO> cap = ArgumentCaptor.forClass(StockMovementRequestDTO.class);
        verify(productService).registerStockMovement(cap.capture());
        assertEquals("OUT", cap.getValue().getType());
        assertEquals(5, cap.getValue().getQuantity());
        assertTrue(cap.getValue().getReason().contains("OV-2026-0001"));
    }

    @Test
    void deliverOrder_stockFisicoInsuficiente_noDebeEntregar() {
        order.setStatus(SaleOrderStatus.APPROVED);
        product.setCurrentStock(1);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class, () -> saleOrderService.deliverOrder(1L));
        verify(productService, never()).registerStockMovement(any());
    }

    @Test
    void deliverOrder_ordenPending_debeLanzarExcepcion() {
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class, () -> saleOrderService.deliverOrder(1L));
    }

    // ── cancelOrder ──────────────────────────────────────────────────────────

    @Test
    void cancelOrder_desdeApproved_debeLibrerarReserva() {
        order.setStatus(SaleOrderStatus.APPROVED);
        product.setReservedStock(5);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.save(any())).thenReturn(product);

        saleOrderService.cancelOrder(1L);

        assertEquals(0, product.getReservedStock());
        assertEquals(SaleOrderStatus.CANCELLED, order.getStatus());
        assertNotNull(order.getCancelledAt());
    }

    @Test
    void cancelOrder_desdePending_noDebeModificarStock() {
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        saleOrderService.cancelOrder(1L);

        assertEquals(SaleOrderStatus.CANCELLED, order.getStatus());
        verify(productRepository, never()).save(any());
    }

    @Test
    void cancelOrder_desdeDelivered_debeLanzarExcepcion() {
        order.setStatus(SaleOrderStatus.DELIVERED);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class, () -> saleOrderService.cancelOrder(1L));
    }

    @Test
    void cancelOrder_yaCancelada_debeLanzarExcepcion() {
        order.setStatus(SaleOrderStatus.CANCELLED);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(RuntimeException.class, () -> saleOrderService.cancelOrder(1L));
    }

    // ── addDetail ────────────────────────────────────────────────────────────

    @Test
    void addDetail_unitCostCapturado() {
        order.getDetails().clear();
        order.setTotalAmount(BigDecimal.ZERO);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(saleOrderDetailRepository.existsBySaleOrderIdAndProductId(1L, 10L)).thenReturn(false);

        SaleOrderDetailRequestDTO dto = SaleOrderDetailRequestDTO.builder()
                .productId(10L).quantity(3).unitPrice(new BigDecimal("200.00")).build();
        SaleOrderDetail newDetail = SaleOrderDetail.builder()
                .quantity(3).unitPrice(new BigDecimal("200.00")).build();
        when(saleOrderDetailMapper.toEntity(dto)).thenReturn(newDetail);

        saleOrderService.addDetail(1L, dto);

        assertEquals(new BigDecimal("1000.00"), newDetail.getUnitCost());
        assertEquals(new BigDecimal("600.00"), newDetail.getSubtotal());
    }

    @Test
    void addDetail_productoDuplicado_debeLanzarExcepcion() {
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(saleOrderDetailRepository.existsBySaleOrderIdAndProductId(1L, 10L)).thenReturn(true);

        SaleOrderDetailRequestDTO dto = SaleOrderDetailRequestDTO.builder()
                .productId(10L).quantity(1).unitPrice(BigDecimal.ONE).build();

        assertThrows(RuntimeException.class, () -> saleOrderService.addDetail(1L, dto));
        verify(saleOrderRepository, never()).save(any());
    }

    @Test
    void addDetail_ordenNoEnPending_debeLanzarExcepcion() {
        order.setStatus(SaleOrderStatus.APPROVED);
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        SaleOrderDetailRequestDTO dto = SaleOrderDetailRequestDTO.builder()
                .productId(10L).quantity(1).unitPrice(BigDecimal.ONE).build();

        assertThrows(RuntimeException.class, () -> saleOrderService.addDetail(1L, dto));
    }

    // ── updateDetail ─────────────────────────────────────────────────────────

    @Test
    void updateDetail_unitCostReLeido() {
        product.setUnitCost(new BigDecimal("90.00"));
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(saleOrderDetailRepository.findByIdAndSaleOrderId(1L, 1L))
                .thenReturn(Optional.of(detail));

        SaleOrderDetailUpdateRequestDTO dto = SaleOrderDetailUpdateRequestDTO.builder()
                .quantity(3).unitPrice(new BigDecimal("1600.00")).build();

        saleOrderService.updateDetail(1L, 1L, dto);

        assertEquals(new BigDecimal("90.00"), detail.getUnitCost());
        assertEquals(new BigDecimal("4800.00"), detail.getSubtotal());
    }

    @Test
    void updateDetail_detalleNoEncontrado_debeLanzarExcepcion() {
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(saleOrderDetailRepository.findByIdAndSaleOrderId(99L, 1L))
                .thenReturn(Optional.empty());

        SaleOrderDetailUpdateRequestDTO dto = SaleOrderDetailUpdateRequestDTO.builder()
                .quantity(1).unitPrice(BigDecimal.ONE).build();

        assertThrows(RuntimeException.class, () -> saleOrderService.updateDetail(1L, 99L, dto));
    }

    // ── removeDetail ─────────────────────────────────────────────────────────

    @Test
    void removeDetail_debeEliminarDetalle() {
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(saleOrderDetailRepository.findByIdAndSaleOrderId(1L, 1L))
                .thenReturn(Optional.of(detail));

        saleOrderService.removeDetail(1L, 1L);

        assertTrue(order.getDetails().isEmpty());
        verify(saleOrderRepository).save(order);
    }

    // ── updateOrder ──────────────────────────────────────────────────────────

    @Test
    void updateOrder_debeAsignarUpdatedBy() {
        when(saleOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        SaleOrderUpdateRequestDTO dto = SaleOrderUpdateRequestDTO.builder()
                .clientId(1L).notes("Nueva nota").build();

        saleOrderService.updateOrder(1L, dto);

        assertEquals(user, order.getUpdatedBy());
        assertNotNull(order.getUpdatedAt());
    }

    // ── findByProductIdAndStatus ──────────────────────────────────────────────

    @Test
    void findByProductIdAndStatus_statusInvalido_debeLanzarExcepcion() {
        assertThrows(RuntimeException.class,
                () -> saleOrderService.findByProductIdAndStatus(10L, "INVALIDO"));
    }

    @Test
    void findByProductIdAndStatus_productoInexistente_debeLanzarExcepcion() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> saleOrderService.findByProductIdAndStatus(99L, "PENDING"));
    }
}
