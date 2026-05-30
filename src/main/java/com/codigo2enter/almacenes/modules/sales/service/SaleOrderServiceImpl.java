package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.inventory.service.ProductService;
import com.codigo2enter.almacenes.modules.sales.dto.*;
import com.codigo2enter.almacenes.modules.sales.mapper.SaleOrderDetailMapper;
import com.codigo2enter.almacenes.modules.sales.mapper.SaleOrderMapper;
import com.codigo2enter.almacenes.modules.sales.model.Client;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrder;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderDetail;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus;
import com.codigo2enter.almacenes.modules.sales.repository.ClientRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderDetailRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements SaleOrderService {

    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderDetailRepository saleOrderDetailRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;

    @Override
    public SaleOrderResponseDTO createOrder(SaleOrderRequestDTO dto) {
        Client client = findClientOrThrow(dto.getClientId());
        User creator = resolveAuthenticatedUser();

        SaleOrder order = SaleOrder.builder()
                .orderNumber(generateOrderNumber())
                .client(client)
                .createdBy(creator)
                .notes(dto.getNotes())
                .build();

        for (SaleOrderDetailRequestDTO detailDto : dto.getDetails()) {
            Product product = findActiveProductOrThrow(detailDto.getProductId());
            BigDecimal subtotal = detailDto.getUnitPrice()
                    .multiply(BigDecimal.valueOf(detailDto.getQuantity()));

            SaleOrderDetail detail = saleOrderDetailMapper.toEntity(detailDto);
            detail.setProduct(product);
            detail.setSaleOrder(order);
            detail.setSubtotal(subtotal);
            detail.setUnitCost(product.getUnitCost());
            order.getDetails().add(detail);
        }

        calculateTotal(order);
        return saleOrderMapper.toResponseDTO(saleOrderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public SaleOrderResponseDTO findById(Long id) {
        return saleOrderMapper.toResponseDTO(findOrderOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleOrderResponseDTO> findByStatus(String status) {
        return saleOrderMapper.toResponseDTOList(
                saleOrderRepository.findByStatus(parseStatus(status)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleOrderResponseDTO> findByClientId(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new RuntimeException("Cliente con id " + clientId + " no encontrado.");
        }
        return saleOrderMapper.toResponseDTOList(
                saleOrderRepository.findByClientId(clientId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleOrderResponseDTO> findByClientIdAndStatus(Long clientId, String status) {
        if (!clientRepository.existsById(clientId)) {
            throw new RuntimeException("Cliente con id " + clientId + " no encontrado.");
        }
        return saleOrderMapper.toResponseDTOList(
                saleOrderRepository.findByClientIdAndStatus(clientId, parseStatus(status)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleOrderResponseDTO> findByProductId(Long productId) {
        findActiveProductOrThrow(productId);
        return saleOrderMapper.toResponseDTOList(
                saleOrderRepository.findByProductId(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleOrderResponseDTO> findByProductIdAndStatus(Long productId, String status) {
        findActiveProductOrThrow(productId);
        return saleOrderMapper.toResponseDTOList(
                saleOrderRepository.findByProductIdAndStatus(productId, parseStatus(status)));
    }

    @Override
    public SaleOrderResponseDTO updateOrder(Long id, SaleOrderUpdateRequestDTO dto) {
        SaleOrder order = findOrderOrThrow(id);
        validatePending(order);
        Client client = findClientOrThrow(dto.getClientId());
        order.setClient(client);
        order.setNotes(dto.getNotes());
        order.setUpdatedAt(LocalDateTime.now());
        order.setUpdatedBy(resolveAuthenticatedUser());
        return saleOrderMapper.toResponseDTO(saleOrderRepository.save(order));
    }

    /**
     * Aprobación en dos fases para evitar reservas parciales:
     * Fase 1 — validar disponibilidad de todos los productos (sin writes).
     * Fase 2 — reservar todos los productos (writes).
     * Si la Fase 1 falla en cualquier producto, ninguno se reserva.
     */
    @Override
    public SaleOrderResponseDTO approveOrder(Long id) {
        SaleOrder order = findOrderOrThrow(id);
        if (order.getStatus() != SaleOrderStatus.PENDING) {
            throw new RuntimeException(
                "Solo se pueden aprobar órdenes en estado PENDING. Estado actual: " + order.getStatus());
        }
        if (order.getDetails().isEmpty()) {
            throw new RuntimeException("No se puede aprobar una orden sin detalles.");
        }

        // FASE 1: validar sin escribir
        for (SaleOrderDetail detail : order.getDetails()) {
            Product product = detail.getProduct();
            int available = product.getCurrentStock() - product.getReservedStock();
            if (available < detail.getQuantity()) {
                throw new RuntimeException(
                    "Stock disponible insuficiente para '" + product.getName() + "'. " +
                    "Disponible: " + available + ", solicitado: " + detail.getQuantity() + ".");
            }
        }

        // FASE 2: reservar.
        // saveAndFlush() fuerza el SQL UPDATE inmediatamente en lugar de esperar al
        // commit de la transacción. Esto es necesario para que @Version haga la
        // verificación de versión dentro de este try-catch. Con save() simple,
        // el flush ocurre al cerrar la transacción (después de salir del try-catch)
        // y ObjectOptimisticLockingFailureException llegaría al GlobalExceptionHandler
        // sin el mensaje de negocio correcto.
        try {
            for (SaleOrderDetail detail : order.getDetails()) {
                Product product = detail.getProduct();
                product.setReservedStock(product.getReservedStock() + detail.getQuantity());
                productRepository.saveAndFlush(product);
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new RuntimeException(
                "Stock modificado concurrentemente. Intente nuevamente.");
        }

        User actor = resolveAuthenticatedUser();
        order.setStatus(SaleOrderStatus.APPROVED);
        order.setApprovedAt(LocalDateTime.now());
        order.setApprovedBy(actor);
        order.setUpdatedAt(LocalDateTime.now());
        order.setUpdatedBy(actor);
        return saleOrderMapper.toResponseDTO(saleOrderRepository.save(order));
    }

    /**
     * Entrega: libera la reserva y genera el movimiento OUT en una sola transacción.
     * Se valida contra currentStock (no availableStock) porque la reserva de ESTA
     * orden ya está contabilizada en reservedStock — usar availableStock provocaría
     * una doble resta.
     */
    @Override
    public SaleOrderResponseDTO deliverOrder(Long id) {
        SaleOrder order = findOrderOrThrow(id);
        if (order.getStatus() != SaleOrderStatus.APPROVED) {
            throw new RuntimeException(
                "Solo se pueden entregar órdenes en estado APPROVED. Estado actual: " + order.getStatus());
        }

        // Verificación final contra stock físico
        for (SaleOrderDetail detail : order.getDetails()) {
            Product product = detail.getProduct();
            if (product.getCurrentStock() < detail.getQuantity()) {
                throw new RuntimeException(
                    "Stock físico insuficiente para '" + product.getName() + "'. " +
                    "Físico: " + product.getCurrentStock() + ", requerido: " + detail.getQuantity() + ".");
            }
        }

        // Liberar reserva y registrar movimiento OUT
        for (SaleOrderDetail detail : order.getDetails()) {
            Product product = detail.getProduct();
            product.setReservedStock(product.getReservedStock() - detail.getQuantity());
            productRepository.save(product);

            StockMovementRequestDTO movement = StockMovementRequestDTO.builder()
                    .productId(product.getId())
                    .quantity(detail.getQuantity())
                    .type("OUT")
                    .reason("Entrega orden de venta " + order.getOrderNumber())
                    .build();
            productService.registerStockMovement(movement);
        }

        User actor = resolveAuthenticatedUser();
        order.setStatus(SaleOrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        order.setDeliveredBy(actor);
        order.setUpdatedAt(LocalDateTime.now());
        order.setUpdatedBy(actor);
        return saleOrderMapper.toResponseDTO(saleOrderRepository.save(order));
    }

    @Override
    public SaleOrderResponseDTO cancelOrder(Long id) {
        SaleOrder order = findOrderOrThrow(id);
        if (order.getStatus() == SaleOrderStatus.DELIVERED) {
            throw new RuntimeException("No se puede cancelar una orden ya entregada.");
        }
        if (order.getStatus() == SaleOrderStatus.CANCELLED) {
            throw new RuntimeException("La orden ya está cancelada.");
        }

        // Liberar reservas solo si venía de APPROVED
        if (order.getStatus() == SaleOrderStatus.APPROVED) {
            for (SaleOrderDetail detail : order.getDetails()) {
                Product product = detail.getProduct();
                product.setReservedStock(product.getReservedStock() - detail.getQuantity());
                productRepository.save(product);
            }
        }

        User actor = resolveAuthenticatedUser();
        order.setStatus(SaleOrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(actor);
        order.setUpdatedAt(LocalDateTime.now());
        order.setUpdatedBy(actor);
        return saleOrderMapper.toResponseDTO(saleOrderRepository.save(order));
    }

    @Override
    public SaleOrderResponseDTO addDetail(Long orderId, SaleOrderDetailRequestDTO dto) {
        SaleOrder order = findOrderOrThrow(orderId);
        validatePending(order);

        Product product = findActiveProductOrThrow(dto.getProductId());

        if (saleOrderDetailRepository.existsBySaleOrderIdAndProductId(orderId, dto.getProductId())) {
            throw new RuntimeException(
                "El producto '" + product.getName() + "' ya existe en esta orden. " +
                "Use la opción de actualizar detalle para cambiar la cantidad.");
        }

        BigDecimal subtotal = dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));
        SaleOrderDetail detail = saleOrderDetailMapper.toEntity(dto);
        detail.setProduct(product);
        detail.setSaleOrder(order);
        detail.setSubtotal(subtotal);
        detail.setUnitCost(product.getUnitCost());
        order.getDetails().add(detail);

        calculateTotal(order);
        order.setUpdatedAt(LocalDateTime.now());
        order.setUpdatedBy(resolveAuthenticatedUser());
        return saleOrderMapper.toResponseDTO(saleOrderRepository.save(order));
    }

    @Override
    public SaleOrderResponseDTO updateDetail(Long orderId, Long detailId, SaleOrderDetailUpdateRequestDTO dto) {
        SaleOrder order = findOrderOrThrow(orderId);
        validatePending(order);

        SaleOrderDetail detail = saleOrderDetailRepository
                .findByIdAndSaleOrderId(detailId, orderId)
                .orElseThrow(() -> new RuntimeException(
                    "Detalle con id " + detailId + " no encontrado en la orden " + orderId + "."));

        detail.setQuantity(dto.getQuantity());
        detail.setUnitPrice(dto.getUnitPrice());
        detail.setUnitCost(detail.getProduct().getUnitCost());
        detail.setSubtotal(dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));

        calculateTotal(order);
        order.setUpdatedAt(LocalDateTime.now());
        order.setUpdatedBy(resolveAuthenticatedUser());
        return saleOrderMapper.toResponseDTO(saleOrderRepository.save(order));
    }

    @Override
    public void removeDetail(Long orderId, Long detailId) {
        SaleOrder order = findOrderOrThrow(orderId);
        validatePending(order);

        SaleOrderDetail detail = saleOrderDetailRepository
                .findByIdAndSaleOrderId(detailId, orderId)
                .orElseThrow(() -> new RuntimeException(
                    "Detalle con id " + detailId + " no encontrado en la orden " + orderId + "."));

        order.getDetails().remove(detail);
        calculateTotal(order);
        order.setUpdatedAt(LocalDateTime.now());
        order.setUpdatedBy(resolveAuthenticatedUser());
        saleOrderRepository.save(order);
    }

    // ─── Métodos privados ─────────────────────────────────────────────────────

    private SaleOrder findOrderOrThrow(Long id) {
        return saleOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "Orden de venta con id " + id + " no encontrada."));
    }

    private Client findClientOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "Cliente con id " + id + " no encontrado."));
    }

    private Product findActiveProductOrThrow(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "Producto con id " + id + " no encontrado."));
        if (!product.isActive()) {
            throw new RuntimeException(
                "El producto '" + product.getName() + "' no está activo.");
        }
        return product;
    }

    private void validatePending(SaleOrder order) {
        if (order.getStatus() != SaleOrderStatus.PENDING) {
            throw new RuntimeException(
                "Esta operación solo está permitida en órdenes PENDING. " +
                "Estado actual: " + order.getStatus() + ".");
        }
    }

    private void calculateTotal(SaleOrder order) {
        BigDecimal total = order.getDetails().stream()
                .map(SaleOrderDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
    }

    private User resolveAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(
                    "Usuario autenticado no encontrado en el sistema."));
    }

    private SaleOrderStatus parseStatus(String status) {
        try {
            return SaleOrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                "Estado inválido: '" + status + "'. Use PENDING, APPROVED, DELIVERED o CANCELLED.");
        }
    }

    private String generateOrderNumber() {
        int year = Year.now().getValue();
        long count = saleOrderRepository.countByYear(year);
        String candidate;
        do {
            count++;
            candidate = "OV-" + year + "-" + String.format("%04d", count);
        } while (saleOrderRepository.findByOrderNumber(candidate).isPresent());
        return candidate;
    }
}
