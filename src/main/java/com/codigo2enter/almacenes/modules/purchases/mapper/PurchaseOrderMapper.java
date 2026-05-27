package com.codigo2enter.almacenes.modules.purchases.mapper;

import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct para la conversión entre PurchaseOrder y sus DTOs.
 *
 * Es el mapper más complejo del módulo purchases porque la entidad tiene
 * múltiples relaciones @ManyToOne que deben aplanarse en el DTO de respuesta:
 *   supplier  → supplierId + supplierName  (supplier.companyName)
 *   createdBy → createdById + createdByUsername
 *   details   → delegado a PurchaseOrderDetailMapper vía uses
 *
 * El campo 'status' (enum PurchaseOrderStatus → String) se convierte
 * automáticamente por MapStruct llamando a PurchaseOrderStatus.name(),
 * produciendo "PENDING", "APPROVED", etc. directamente — sin @Mapping explícito.
 *
 * Se declara uses = {PurchaseOrderDetailMapper.class} para que MapStruct
 * delegue la conversión de List<PurchaseOrderDetail> → List<PurchaseOrderDetailResponseDTO>
 * al mapper especializado, reutilizando su lógica de aplanamiento de Product.
 */
@Mapper(componentModel = "spring", uses = {PurchaseOrderDetailMapper.class})
public interface PurchaseOrderMapper {

    /**
     * Convierte una entidad PurchaseOrder a su DTO de respuesta completo.
     *
     * Mappings especiales:
     *   supplier.id          → supplierId
     *   supplier.companyName → supplierName  (no supplier.name — el campo real es companyName)
     *   createdBy.id         → createdById
     *   createdBy.username   → createdByUsername
     *   details              → delegado automáticamente a PurchaseOrderDetailMapper
     *                          gracias a uses = {PurchaseOrderDetailMapper.class}
     *   status (enum)        → status (String) automático vía Enum.name()
     *
     * Requiere transacción activa para resolver los proxies LAZY de supplier,
     * createdBy y los detalles sin LazyInitializationException.
     *
     * @param order entidad persistida con todas sus relaciones cargables
     * @return DTO completo listo para serializar como JSON en la respuesta HTTP
     */
    @Mapping(source = "supplier.id",          target = "supplierId")
    @Mapping(source = "supplier.companyName", target = "supplierName")
    @Mapping(source = "createdBy.id",         target = "createdById")
    @Mapping(source = "createdBy.username",   target = "createdByUsername")
    PurchaseOrderResponseDTO toResponseDTO(PurchaseOrder order);

    /**
     * Convierte una lista de órdenes a lista de DTOs de respuesta.
     * MapStruct aplica toResponseDTO() a cada elemento automáticamente.
     * Usado en findByStatus, findBySupplierId y findOrdersByProduct.
     *
     * @param orders lista de órdenes recuperadas de la base de datos
     * @return lista de DTOs lista para serializar como JSON array
     */
    List<PurchaseOrderResponseDTO> toResponseDTOList(List<PurchaseOrder> orders);

    /**
     * Convierte un PurchaseOrderRequestDTO en una nueva entidad PurchaseOrder.
     *
     * Campos ignorados y motivo:
     *   - id          : generado por PostgreSQL con IDENTITY
     *   - orderNumber : generado por el servicio con formato OC-YYYY-NNNN
     *   - status      : inicializado como PENDING por el servicio (@Builder.Default)
     *   - totalAmount : calculado por el servicio como suma de subtotales
     *   - supplier    : resuelto por el servicio desde supplierId via SupplierRepository
     *   - createdBy   : resuelto por el servicio desde SecurityContextHolder (JWT)
     *   - details     : procesados individualmente por el servicio en createOrder
     *   - createdAt   : inicializado por @Builder.Default = now()
     *   - updatedAt   : null al crear, actualizado en cada operación posterior
     *   - approvedAt  : null al crear, asignado al aprobar
     *   - receivedAt  : null al crear, asignado al recibir
     *   - cancelledAt : null al crear, asignado al cancelar
     *
     * @param dto datos de la nueva orden enviados por el cliente
     * @return entidad PurchaseOrder lista para que el servicio complete
     *         supplier, createdBy, details y orderNumber antes de persistir
     */
    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "status",      ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "supplier",    ignore = true)
    @Mapping(target = "createdBy",   ignore = true)
    @Mapping(target = "details",     ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   ignore = true)
    @Mapping(target = "approvedAt",  ignore = true)
    @Mapping(target = "receivedAt",  ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    PurchaseOrder toEntity(PurchaseOrderRequestDTO dto);
}
