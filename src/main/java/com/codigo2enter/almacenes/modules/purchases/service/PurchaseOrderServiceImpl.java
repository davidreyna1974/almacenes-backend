package com.codigo2enter.almacenes.modules.purchases.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementRequestDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

/**
 * Implementación concreta de PurchaseOrderService.
 *
 * Gestiona el ciclo de vida completo de una orden de compra y su integración
 * con el módulo de inventario. El método receiveOrder es el punto de integración
 * crítico: ejecuta movimientos de stock IN dentro de la misma transacción que
 * el cambio de estado, garantizando consistencia entre órdenes e inventario.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository     purchaseOrderRepository;
    private final PurchaseOrderDetailRepository purchaseOrderDetailRepository;
    private final SupplierRepository          supplierRepository;
    private final ProductRepository           productRepository;
    private final UserRepository              userRepository;
    private final ProductService              productService;
    private final PurchaseOrderMapper         purchaseOrderMapper;
    private final PurchaseOrderDetailMapper   purchaseOrderDetailMapper;

    /**
     * {@inheritDoc}
     *
     * Flujo completo:
     *   1. Valida existencia del proveedor
     *   2. Resuelve el usuario autenticado desde el JWT
     *   3. Genera el orderNumber único con formato OC-YYYY-NNNN
     *   4. Construye la orden base con status PENDING
     *   5. Procesa cada detalle: valida producto, calcula subtotal, agrega a la orden
     *   6. Calcula totalAmount y persiste toda la orden con sus detalles en una transacción
     */
    @Override
    public PurchaseOrderResponseDTO createOrder(PurchaseOrderRequestDTO dto) {
        Supplier supplier = findSupplierOrThrow(dto.getSupplierId());
        User creator = resolveAuthenticatedUser();

        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(generateOrderNumber())
                .supplier(supplier)
                .createdBy(creator)
                .notes(dto.getNotes())
                .build();

        // Procesar cada detalle del request
        for (PurchaseOrderDetailRequestDTO detailDto : dto.getDetails()) {
            var product = productRepository.findById(detailDto.getProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Producto con id " + detailDto.getProductId() + " no encontrado."));

            BigDecimal subtotal = detailDto.getUnitPrice()
                    .multiply(BigDecimal.valueOf(detailDto.getQuantity()));

            PurchaseOrderDetail detail = purchaseOrderDetailMapper.toEntity(detailDto);
            detail.setProduct(product);
            detail.setPurchaseOrder(order);
            detail.setSubtotal(subtotal);

            order.getDetails().add(detail);
        }

        calculateTotal(order);
        return purchaseOrderMapper.toResponseDTO(purchaseOrderRepository.save(order));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponseDTO findById(Long id) {
        return purchaseOrderMapper.toResponseDTO(findOrderOrThrow(id));
    }

    /**
     * {@inheritDoc}
     *
     * Convierte el String recibido al enum con try/catch antes de consultar
     * el repositorio — mismo patrón que registerStockMovement en inventory.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDTO> findByStatus(String status) {
        PurchaseOrderStatus statusEnum;
        try {
            statusEnum = PurchaseOrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Estado inválido: '" + status + "'. Valores permitidos: " +
                    "PENDING, APPROVED, RECEIVED, CANCELLED.");
        }
        return purchaseOrderMapper.toResponseDTOList(
                purchaseOrderRepository.findByStatus(statusEnum));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDTO> findBySupplierId(Long supplierId) {
        findSupplierOrThrow(supplierId);
        return purchaseOrderMapper.toResponseDTOList(
                purchaseOrderRepository.findBySupplierId(supplierId));
    }

    /**
     * {@inheritDoc}
     *
     * Valida existencia del producto antes de consultar sus órdenes para
     * distinguir "producto sin órdenes" (lista vacía) de "producto no existe" (error).
     */
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDTO> findOrdersByProduct(Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException(
                        "Producto con id " + productId + " no encontrado."));
        return purchaseOrderMapper.toResponseDTOList(
                purchaseOrderRepository.findByProductId(productId));
    }

    /**
     * {@inheritDoc}
     *
     * Solo edita notes y supplier. Si supplierId cambió, resuelve la nueva
     * entidad Supplier. Hibernate dirty-checking persiste los cambios.
     */
    @Override
    public PurchaseOrderResponseDTO updateOrder(Long id, PurchaseOrderUpdateRequestDTO dto) {
        PurchaseOrder order = findOrderOrThrow(id);
        validatePending(order, "editar");

        Supplier supplier = findSupplierOrThrow(dto.getSupplierId());
        order.setSupplier(supplier);
        order.setNotes(dto.getNotes());
        order.setUpdatedAt(LocalDateTime.now());

        return purchaseOrderMapper.toResponseDTO(order);
    }

    /**
     * {@inheritDoc}
     *
     * Valida que la orden tenga al menos un detalle antes de aprobar —
     * no tiene sentido autorizar una orden vacía.
     */
    @Override
    public PurchaseOrderResponseDTO approveOrder(Long id) {
        PurchaseOrder order = findOrderOrThrow(id);

        if (order.getStatus() != PurchaseOrderStatus.PENDING) {
            throw new RuntimeException(
                    "Solo se pueden aprobar órdenes en estado PENDING. " +
                    "Estado actual: " + order.getStatus());
        }
        if (order.getDetails().isEmpty()) {
            throw new RuntimeException(
                    "No se puede aprobar una orden sin líneas de detalle.");
        }

        order.setStatus(PurchaseOrderStatus.APPROVED);
        order.setApprovedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return purchaseOrderMapper.toResponseDTO(order);
    }

    /**
     * {@inheritDoc}
     *
     * Punto de integración con el módulo inventory. Por cada detalle construye
     * un StockMovementRequestDTO y delega a productService.registerStockMovement().
     * Al compartir la misma transacción (@Transactional propagation=REQUIRED),
     * si cualquier movimiento falla Hibernate hace rollback de toda la operación.
     */
    @Override
    public PurchaseOrderResponseDTO receiveOrder(Long id) {
        PurchaseOrder order = findOrderOrThrow(id);

        if (order.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw new RuntimeException(
                    "Solo se pueden recibir órdenes en estado APPROVED. " +
                    "Estado actual: " + order.getStatus());
        }

        // Disparar movimiento IN por cada línea de detalle
        for (PurchaseOrderDetail detail : order.getDetails()) {
            StockMovementRequestDTO movement = StockMovementRequestDTO.builder()
                    .productId(detail.getProduct().getId())
                    .quantity(detail.getQuantity())
                    .type("IN")
                    .reason("Recepción orden de compra " + order.getOrderNumber())
                    .build();
            productService.registerStockMovement(movement);
        }

        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.setReceivedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return purchaseOrderMapper.toResponseDTO(order);
    }

    /**
     * {@inheritDoc}
     *
     * Solo PENDING y APPROVED pueden cancelarse — RECEIVED y CANCELLED son terminales.
     */
    @Override
    public PurchaseOrderResponseDTO cancelOrder(Long id) {
        PurchaseOrder order = findOrderOrThrow(id);

        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new RuntimeException(
                    "No se puede cancelar una orden ya recibida.");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new RuntimeException(
                    "La orden ya está cancelada.");
        }

        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return purchaseOrderMapper.toResponseDTO(order);
    }

    /**
     * {@inheritDoc}
     *
     * Valida que el producto no esté ya en la orden para evitar duplicados —
     * si se necesita más cantidad de un producto existente, usar updateDetail.
     */
    @Override
    public PurchaseOrderResponseDTO addDetail(Long orderId, PurchaseOrderDetailRequestDTO dto) {
        PurchaseOrder order = findOrderOrThrow(orderId);
        validatePending(order, "agregar detalles a");

        var product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new RuntimeException(
                        "Producto con id " + dto.getProductId() + " no encontrado."));

        if (purchaseOrderDetailRepository.existsByPurchaseOrderIdAndProductId(
                orderId, dto.getProductId())) {
            throw new RuntimeException(
                    "El producto '" + product.getName() + "' ya está en esta orden. " +
                    "Use el endpoint de actualización para cambiar la cantidad.");
        }

        BigDecimal subtotal = dto.getUnitPrice()
                .multiply(BigDecimal.valueOf(dto.getQuantity()));

        PurchaseOrderDetail detail = purchaseOrderDetailMapper.toEntity(dto);
        detail.setProduct(product);
        detail.setPurchaseOrder(order);
        detail.setSubtotal(subtotal);

        order.getDetails().add(detail);
        calculateTotal(order);
        order.setUpdatedAt(LocalDateTime.now());

        return purchaseOrderMapper.toResponseDTO(purchaseOrderRepository.save(order));
    }

    /**
     * {@inheritDoc}
     *
     * findByIdAndPurchaseOrderId valida en una sola query que el detalle existe
     * y pertenece a la orden indicada, protegiendo contra accesos cruzados entre órdenes.
     */
    @Override
    public PurchaseOrderResponseDTO updateDetail(Long orderId, Long detailId,
                                                  PurchaseOrderDetailUpdateRequestDTO dto) {
        PurchaseOrder order = findOrderOrThrow(orderId);
        validatePending(order, "editar detalles de");

        PurchaseOrderDetail detail = purchaseOrderDetailRepository
                .findByIdAndPurchaseOrderId(detailId, orderId)
                .orElseThrow(() -> new RuntimeException(
                        "Detalle con id " + detailId + " no encontrado o no pertenece a esta orden."));

        detail.setQuantity(dto.getQuantity());
        detail.setUnitPrice(dto.getUnitPrice());
        detail.setSubtotal(dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));

        calculateTotal(order);
        order.setUpdatedAt(LocalDateTime.now());

        return purchaseOrderMapper.toResponseDTO(purchaseOrderRepository.save(order));
    }

    /**
     * {@inheritDoc}
     *
     * La eliminación física del detalle la ejecuta Hibernate automáticamente
     * al removerlo de la lista order.details gracias a orphanRemoval=true
     * en la relación @OneToMany de PurchaseOrder.
     */
    @Override
    public void removeDetail(Long orderId, Long detailId) {
        PurchaseOrder order = findOrderOrThrow(orderId);
        validatePending(order, "eliminar detalles de");

        PurchaseOrderDetail detail = purchaseOrderDetailRepository
                .findByIdAndPurchaseOrderId(detailId, orderId)
                .orElseThrow(() -> new RuntimeException(
                        "Detalle con id " + detailId + " no encontrado o no pertenece a esta orden."));

        // orphanRemoval=true hace que Hibernate elimine físicamente el detalle
        // al removerlo de la lista y guardar la orden.
        order.getDetails().remove(detail);
        calculateTotal(order);
        order.setUpdatedAt(LocalDateTime.now());
        purchaseOrderRepository.save(order);
    }

    // =========================================================================
    // Métodos privados de apoyo
    // =========================================================================

    /**
     * Busca una orden por ID o lanza RuntimeException si no existe.
     */
    private PurchaseOrder findOrderOrThrow(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Orden de compra con id " + id + " no encontrada."));
    }

    /**
     * Busca un proveedor por ID o lanza RuntimeException si no existe.
     */
    private Supplier findSupplierOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Proveedor con id " + id + " no encontrado."));
    }

    /**
     * Resuelve el usuario autenticado desde el JWT en SecurityContextHolder.
     * El filtro JWT deposita el username en el contexto antes de llegar al servicio.
     */
    private User resolveAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(
                        "Usuario autenticado no encontrado en el sistema."));
    }

    /**
     * Recalcula totalAmount de la orden como suma de todos los subtotales.
     * Se llama después de cada operación que modifica los detalles:
     * addDetail, updateDetail y removeDetail.
     */
    private void calculateTotal(PurchaseOrder order) {
        BigDecimal total = order.getDetails().stream()
                .map(PurchaseOrderDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
    }

    /**
     * Valida que la orden esté en estado PENDING antes de permitir operaciones
     * de edición. Centraliza la validación para no repetirla en cada método.
     *
     * @param order  orden a validar
     * @param action descripción de la acción para el mensaje de error
     */
    private void validatePending(PurchaseOrder order, String action) {
        if (order.getStatus() != PurchaseOrderStatus.PENDING) {
            throw new RuntimeException(
                    "Solo se pueden " + action + " órdenes en estado PENDING. " +
                    "Estado actual: " + order.getStatus());
        }
    }

    /**
     * Genera un número de orden único con formato OC-YYYY-NNNN.
     * Usa un contador anual que se reinicia cada año — OC-2026-0001 es el
     * primero de 2026, OC-2027-0001 es el primero de 2027.
     * En caso de colisión (muy improbable), incrementa la secuencia hasta
     * encontrar un número disponible.
     */
    private String generateOrderNumber() {
        int year = Year.now().getValue();
        long count = purchaseOrderRepository.countByYear(year);
        String candidate;
        do {
            count++;
            candidate = "OC-" + year + "-" + String.format("%04d", count);
        } while (purchaseOrderRepository.findByOrderNumber(candidate).isPresent());
        return candidate;
    }
}
