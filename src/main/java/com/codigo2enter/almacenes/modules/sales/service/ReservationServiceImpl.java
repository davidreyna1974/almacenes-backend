package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.core.exception.ResourceNotFoundException;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrder;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderDetail;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus;
import com.codigo2enter.almacenes.modules.sales.repository.ClientRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio de solo lectura para visibilidad de reservas activas.
 *
 * Todos los métodos son readOnly — nunca modifican estado. Los DTOs se construyen
 * directamente en el servicio (sin mappers) porque son vistas agregadas que cruzan
 * varias entidades y no tienen una correspondencia 1-a-1 con ninguna entidad JPA.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ProductRepository productRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final ClientRepository clientRepository;

    @Override
    public ReservationSummaryDTO getSummary() {
        List<Product> productsWithReservations = productRepository.findProductsWithActiveReservations();
        List<SaleOrder> approvedOrders = saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED);

        int totalUnits = productsWithReservations.stream()
                .mapToInt(Product::getReservedStock)
                .sum();

        BigDecimal totalValue = productsWithReservations.stream()
                .map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getReservedStock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ReservationSummaryDTO.builder()
                .totalProductsWithReservations(productsWithReservations.size())
                .totalReservedUnits(totalUnits)
                .totalReservedValue(totalValue)
                .totalApprovedOrders(approvedOrders.size())
                .build();
    }

    @Override
    public List<ReservedProductDTO> getReservedProducts() {
        List<Product> products = productRepository.findProductsWithActiveReservations();
        List<SaleOrder> approvedOrders = saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED);

        return products.stream()
                .map(product -> buildReservedProductDTO(product, approvedOrders))
                .collect(Collectors.toList());
    }

    @Override
    public ReservedProductDTO getProductReservationDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Producto con id " + productId + " no encontrado."));

        List<SaleOrder> approvedOrders = saleOrderRepository
                .findByProductIdAndStatus(productId, SaleOrderStatus.APPROVED);

        return buildReservedProductDTO(product, approvedOrders);
    }

    @Override
    public List<ReservedClientDTO> getClientsWithReservations() {
        List<SaleOrder> approvedOrders = saleOrderRepository.findByStatus(SaleOrderStatus.APPROVED);

        Map<Long, List<SaleOrder>> ordersByClient = approvedOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getClient().getId()));

        return ordersByClient.entrySet().stream()
                .map(entry -> buildReservedClientDTO(
                        entry.getValue().get(0).getClient().getId(),
                        entry.getValue().get(0).getClient().getName(),
                        entry.getValue()))
                .sorted((a, b) -> b.getTotalReservedValue().compareTo(a.getTotalReservedValue()))
                .collect(Collectors.toList());
    }

    @Override
    public ReservedClientDTO getClientReservationDetail(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Cliente con id " + clientId + " no encontrado.");
        }

        List<SaleOrder> approvedOrders = saleOrderRepository
                .findByClientIdAndStatus(clientId, SaleOrderStatus.APPROVED);

        String clientName = approvedOrders.isEmpty()
                ? clientRepository.findById(clientId).map(c -> c.getName()).orElse("")
                : approvedOrders.get(0).getClient().getName();

        return buildReservedClientDTO(clientId, clientName, approvedOrders);
    }

    // ─── Constructores de DTOs ────────────────────────────────────────────────

    private ReservedProductDTO buildReservedProductDTO(Product product, List<SaleOrder> allApprovedOrders) {
        List<ReservedProductOrderDTO> orderDTOs = new ArrayList<>();

        for (SaleOrder order : allApprovedOrders) {
            order.getDetails().stream()
                    .filter(d -> d.getProduct().getId().equals(product.getId()))
                    .forEach(d -> orderDTOs.add(ReservedProductOrderDTO.builder()
                            .orderId(order.getId())
                            .orderNumber(order.getOrderNumber())
                            .quantity(d.getQuantity())
                            .subtotal(d.getSubtotal())
                            .clientId(order.getClient().getId())
                            .clientName(order.getClient().getName())
                            .approvedAt(order.getApprovedAt())
                            .build()));
        }

        BigDecimal totalValue = product.getPrice()
                .multiply(BigDecimal.valueOf(product.getReservedStock()));

        return ReservedProductDTO.builder()
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .totalReservedQty(product.getReservedStock())
                .unitPrice(product.getPrice())
                .totalReservedValue(totalValue)
                .orders(orderDTOs)
                .build();
    }

    private ReservedClientDTO buildReservedClientDTO(Long clientId, String clientName, List<SaleOrder> orders) {
        List<ReservedClientOrderDTO> orderDTOs = orders.stream()
                .map(o -> ReservedClientOrderDTO.builder()
                        .orderId(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .totalAmount(o.getTotalAmount())
                        .approvedAt(o.getApprovedAt())
                        .totalItems(o.getDetails().size())
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalValue = orders.stream()
                .map(SaleOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ReservedClientDTO.builder()
                .clientId(clientId)
                .clientName(clientName)
                .totalReservedOrders(orders.size())
                .totalReservedValue(totalValue)
                .orders(orderDTOs)
                .build();
    }
}
