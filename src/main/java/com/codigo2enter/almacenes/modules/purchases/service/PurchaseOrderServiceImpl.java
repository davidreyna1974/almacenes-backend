package com.codigo2enter.almacenes.modules.purchases.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.core.exception.BusinessRuleException;
import com.codigo2enter.almacenes.core.exception.ResourceNotFoundException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@Slf4j
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
                    .orElseThrow(() -> new ResourceNotFoundException(
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
        return toResponseDTOFiltered(purchaseOrderRepository.save(order));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponseDTO findById(Long id) {
        return toResponseDTOFiltered(findOrderOrThrow(id));
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
            throw new BusinessRuleException(
                    "Estado inválido: '" + status + "'. Valores permitidos: " +
                    "PENDING, APPROVED, RECEIVED, CANCELLED.");
        }
        return toResponseDTOListFiltered(
                purchaseOrderRepository.findByStatus(statusEnum));
    }

    /**
     * {@inheritDoc}
     *
     * Sort por createdAt DESC para que las órdenes más recientes aparezcan
     * primero — el operador generalmente gestiona las órdenes más nuevas.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<PurchaseOrderResponseDTO> findByStatus(String status, int page, int size) {
        PurchaseOrderStatus statusEnum;
        try {
            statusEnum = PurchaseOrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(
                    "Estado inválido: '" + status + "'. Valores permitidos: " +
                    "PENDING, APPROVED, RECEIVED, CANCELLED.");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PurchaseOrder> result = purchaseOrderRepository.findByStatus(statusEnum, pageable);
        return PageResponseDTO.from(result.map(this::toResponseDTOFiltered));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDTO> findBySupplierId(Long supplierId) {
        findSupplierOrThrow(supplierId);
        return toResponseDTOListFiltered(
                purchaseOrderRepository.findBySupplierId(supplierId));
    }

    /**
     * {@inheritDoc}
     *
     * Combina las validaciones de findBySupplierId (proveedor existe) y
     * findByStatus (conversión String→enum con try/catch) antes de consultar
     * el repositorio con el filtro doble.
     *
     * El orden de validaciones:
     *   1. Convertir status String → enum — falla rápido si el valor es inválido,
     *      antes de consultar la BD.
     *   2. Validar que el proveedor exista — distingue "proveedor sin órdenes en
     *      ese estado" (lista vacía válida) de "proveedor inexistente" (error).
     */
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponseDTO> findBySupplierIdAndStatus(Long supplierId, String status) {
        PurchaseOrderStatus statusEnum;
        try {
            statusEnum = PurchaseOrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(
                    "Estado inválido: '" + status + "'. Valores permitidos: " +
                    "PENDING, APPROVED, RECEIVED, CANCELLED.");
        }
        findSupplierOrThrow(supplierId);
        return toResponseDTOListFiltered(
                purchaseOrderRepository.findBySupplierIdAndStatus(supplierId, statusEnum));
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Producto con id " + productId + " no encontrado."));
        return toResponseDTOListFiltered(
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

        return toResponseDTOFiltered(order);
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
            throw new BusinessRuleException(
                    "Solo se pueden aprobar órdenes en estado PENDING. " +
                    "Estado actual: " + order.getStatus());
        }
        if (order.getDetails().isEmpty()) {
            throw new BusinessRuleException(
                    "No se puede aprobar una orden sin líneas de detalle.");
        }

        order.setStatus(PurchaseOrderStatus.APPROVED);
        order.setApprovedAt(LocalDateTime.now());
        var approver = resolveAuthenticatedUser();
        order.setApprovedBy(approver);
        order.setUpdatedAt(LocalDateTime.now());
        log.info("OC APROBADA orden={} usuario={}", order.getOrderNumber(), approver.getUsername());

        return toResponseDTOFiltered(order);
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
            throw new BusinessRuleException(
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
        var receiver = resolveAuthenticatedUser();
        order.setReceivedBy(receiver);
        order.setUpdatedAt(LocalDateTime.now());
        log.info("OC RECIBIDA orden={} usuario={}", order.getOrderNumber(), receiver.getUsername());

        return toResponseDTOFiltered(order);
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
            throw new BusinessRuleException(
                    "No se puede cancelar una orden ya recibida.");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "La orden ya está cancelada.");
        }

        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        var canceller = resolveAuthenticatedUser();
        order.setCancelledBy(canceller);
        order.setUpdatedAt(LocalDateTime.now());
        log.info("OC CANCELADA orden={} usuario={}", order.getOrderNumber(), canceller.getUsername());

        return toResponseDTOFiltered(order);
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Producto con id " + dto.getProductId() + " no encontrado."));

        if (purchaseOrderDetailRepository.existsByPurchaseOrderIdAndProductId(
                orderId, dto.getProductId())) {
            throw new BusinessRuleException(
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

        return toResponseDTOFiltered(purchaseOrderRepository.save(order));
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Detalle con id " + detailId + " no encontrado o no pertenece a esta orden."));

        detail.setQuantity(dto.getQuantity());
        detail.setUnitPrice(dto.getUnitPrice());
        detail.setSubtotal(dto.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));

        calculateTotal(order);
        order.setUpdatedAt(LocalDateTime.now());

        return toResponseDTOFiltered(purchaseOrderRepository.save(order));
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
                .orElseThrow(() -> new ResourceNotFoundException(
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden de compra con id " + id + " no encontrada."));
    }

    /**
     * Busca un proveedor por ID o lanza RuntimeException si no existe.
     */
    private Supplier findSupplierOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Proveedor con id " + id + " no encontrado."));
    }

    /**
     * Resuelve el usuario autenticado desde el JWT en SecurityContextHolder.
     * El filtro JWT deposita el username en el contexto antes de llegar al servicio.
     */
    private User resolveAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
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
            throw new BusinessRuleException(
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

    /**
     * Indica si el usuario autenticado tiene ÚNICAMENTE ROLE_WAREHOUSEMAN, sin
     * ROLE_ADMIN ni ROLE_MANAGER. Estos dos últimos siempre ven los montos
     * completos (un usuario con ADMIN+WAREHOUSEMAN, como el seed admin, no
     * se considera "solo WAREHOUSEMAN").
     */
    private boolean isWarehousemanOnly() {
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        boolean isWarehouseman = authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_WAREHOUSEMAN"));
        boolean hasFinancialAccess = authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER"));
        return isWarehouseman && !hasFinancialAccess;
    }

    /**
     * Oculta los campos financieros (totalAmount, unitPrice, subtotal) del DTO
     * cuando el usuario autenticado es WAREHOUSEMAN sin ADMIN/MANAGER.
     * Corrige BUG-M3-24 (Excessive Data Exposure — OWASP API3:2023): el backend
     * no debe enviar montos a roles que el frontend ya oculta en la UI.
     */
    private void maskFinancialFields(PurchaseOrderResponseDTO dto) {
        dto.setTotalAmount(null);
        if (dto.getDetails() != null) {
            dto.getDetails().forEach(detail -> {
                detail.setUnitPrice(null);
                detail.setSubtotal(null);
            });
        }
    }

    /**
     * Mapea una orden a su DTO y aplica el filtrado de campos financieros
     * según el rol del usuario autenticado.
     */
    private PurchaseOrderResponseDTO toResponseDTOFiltered(PurchaseOrder order) {
        PurchaseOrderResponseDTO dto = purchaseOrderMapper.toResponseDTO(order);
        if (isWarehousemanOnly()) {
            maskFinancialFields(dto);
        }
        return dto;
    }

    /**
     * Mapea una lista de órdenes a sus DTOs y aplica el filtrado de campos
     * financieros según el rol del usuario autenticado.
     */
    private List<PurchaseOrderResponseDTO> toResponseDTOListFiltered(List<PurchaseOrder> orders) {
        List<PurchaseOrderResponseDTO> dtos = purchaseOrderMapper.toResponseDTOList(orders);
        if (isWarehousemanOnly()) {
            dtos.forEach(this::maskFinancialFields);
        }
        return dtos;
    }
}
