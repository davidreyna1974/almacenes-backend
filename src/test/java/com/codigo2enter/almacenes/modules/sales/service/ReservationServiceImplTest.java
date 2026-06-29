package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.model.*;
import com.codigo2enter.almacenes.modules.sales.repository.ClientRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock SaleOrderRepository saleOrderRepository;
    @Mock ClientRepository clientRepository;
    @InjectMocks ReservationServiceImpl reservationService;

    private Product product;
    private Client client;
    private SaleOrder approvedOrder;
    private SaleOrderDetail detail;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L).name("Taladro").sku("TAL-001").active(true)
                .currentStock(50).reservedStock(5).minimumStock(5)
                .price(new BigDecimal("1500.00"))
                .version(0L)
                .build();

        client = Client.builder().id(1L).name("Comercial Reyes SA").active(true).build();

        detail = SaleOrderDetail.builder()
                .id(1L).quantity(5)
                .unitPrice(new BigDecimal("1500.00"))
                .subtotal(new BigDecimal("7500.00"))
                .product(product)
                .build();

        approvedOrder = SaleOrder.builder()
                .id(1L).orderNumber("OV-2026-0001")
                .status(SaleOrderStatus.APPROVED)
                .client(client)
                .totalAmount(new BigDecimal("7500.00"))
                .approvedAt(LocalDateTime.now())
                .details(new ArrayList<>(List.of(detail)))
                .build();
        detail.setSaleOrder(approvedOrder);
    }

    // ── getSummary ────────────────────────────────────────────────────────────

    @Test
    void getSummary_conReservas_debeCuantificar() {
        when(productRepository.findProductsWithActiveReservations()).thenReturn(List.of(product));
        when(saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED)).thenReturn(List.of(approvedOrder));

        ReservationSummaryDTO result = reservationService.getSummary();

        assertEquals(1, result.getTotalProductsWithReservations());
        assertEquals(5, result.getTotalReservedUnits());
        assertEquals(new BigDecimal("7500.00"), result.getTotalReservedValue());
        assertEquals(1, result.getTotalApprovedOrders());
    }

    @Test
    void getSummary_sinReservas_debeRetornarCeros() {
        when(productRepository.findProductsWithActiveReservations()).thenReturn(List.of());
        when(saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED)).thenReturn(List.of());

        ReservationSummaryDTO result = reservationService.getSummary();

        assertEquals(0, result.getTotalProductsWithReservations());
        assertEquals(0, result.getTotalReservedUnits());
        assertEquals(BigDecimal.ZERO, result.getTotalReservedValue());
        assertEquals(0, result.getTotalApprovedOrders());
    }

    // ── getReservedProducts ───────────────────────────────────────────────────

    @Test
    void getReservedProducts_debeRetornarProductoConOrden() {
        when(productRepository.findProductsWithActiveReservations()).thenReturn(List.of(product));
        when(saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED)).thenReturn(List.of(approvedOrder));

        List<ReservedProductDTO> result = reservationService.getReservedProducts();

        assertEquals(1, result.size());
        assertEquals("TAL-001", result.get(0).getProductSku());
        assertEquals(5, result.get(0).getTotalReservedQty());
        assertEquals(1, result.get(0).getOrders().size());
        assertEquals("OV-2026-0001", result.get(0).getOrders().get(0).getOrderNumber());
    }

    @Test
    void getReservedProducts_sinReservas_debeRetornarListaVacia() {
        when(productRepository.findProductsWithActiveReservations()).thenReturn(List.of());
        when(saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED)).thenReturn(List.of());

        List<ReservedProductDTO> result = reservationService.getReservedProducts();

        assertTrue(result.isEmpty());
    }

    // ── getProductReservationDetail ───────────────────────────────────────────

    @Test
    void getProductReservationDetail_existente_debeRetornarDetalle() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(saleOrderRepository.findByProductIdAndStatus(1L, SaleOrderStatus.APPROVED))
                .thenReturn(List.of(approvedOrder));

        ReservedProductDTO result = reservationService.getProductReservationDetail(1L);

        assertEquals(1L, result.getProductId());
        assertEquals(5, result.getTotalReservedQty());
        assertEquals(1, result.getOrders().size());
        assertEquals("Comercial Reyes SA", result.getOrders().get(0).getClientName());
    }

    @Test
    void getProductReservationDetail_noExistente_debeLanzarExcepcion() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> reservationService.getProductReservationDetail(99L));
    }

    @Test
    void getProductReservationDetail_sinOrdenes_debeRetornarListaVacia() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(saleOrderRepository.findByProductIdAndStatus(1L, SaleOrderStatus.APPROVED))
                .thenReturn(List.of());

        ReservedProductDTO result = reservationService.getProductReservationDetail(1L);

        assertTrue(result.getOrders().isEmpty());
    }

    // ── getClientsWithReservations ────────────────────────────────────────────

    @Test
    void getClientsWithReservations_debeRetornarClienteConOrdenes() {
        when(saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED))
                .thenReturn(List.of(approvedOrder));

        List<ReservedClientDTO> result = reservationService.getClientsWithReservations();

        assertEquals(1, result.size());
        assertEquals("Comercial Reyes SA", result.get(0).getClientName());
        assertEquals(1, result.get(0).getTotalReservedOrders());
        assertEquals(new BigDecimal("7500.00"), result.get(0).getTotalReservedValue());
    }

    @Test
    void getClientsWithReservations_sinOrdenes_debeRetornarListaVacia() {
        when(saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED)).thenReturn(List.of());

        List<ReservedClientDTO> result = reservationService.getClientsWithReservations();

        assertTrue(result.isEmpty());
    }

    // ── getClientReservationDetail ────────────────────────────────────────────

    @Test
    void getClientReservationDetail_existente_debeRetornarDetalle() {
        when(clientRepository.existsById(1L)).thenReturn(true);
        when(saleOrderRepository.findByClientIdAndStatus(1L, SaleOrderStatus.APPROVED))
                .thenReturn(List.of(approvedOrder));

        ReservedClientDTO result = reservationService.getClientReservationDetail(1L);

        assertEquals(1L, result.getClientId());
        assertEquals("Comercial Reyes SA", result.getClientName());
        assertEquals(1, result.getTotalReservedOrders());
        assertEquals(1, result.getOrders().get(0).getTotalItems());
    }

    @Test
    void getClientReservationDetail_noExistente_debeLanzarExcepcion() {
        when(clientRepository.existsById(99L)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> reservationService.getClientReservationDetail(99L));
    }

    @Test
    void getClientReservationDetail_sinOrdenesAprobadas_debeRetornarVacio() {
        when(clientRepository.existsById(1L)).thenReturn(true);
        when(saleOrderRepository.findByClientIdAndStatus(1L, SaleOrderStatus.APPROVED))
                .thenReturn(List.of());
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        ReservedClientDTO result = reservationService.getClientReservationDetail(1L);

        assertEquals(0, result.getTotalReservedOrders());
        assertTrue(result.getOrders().isEmpty());
    }
}
