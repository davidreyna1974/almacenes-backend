package com.codigo2enter.almacenes.modules.purchases.mapper;

import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct para la conversión entre PurchaseOrderDetail y sus DTOs.
 *
 * La conversión más relevante es toResponseDTO: la entidad tiene relaciones
 * @ManyToOne con Product y PurchaseOrder (objetos anidados), pero el DTO
 * de respuesta expone la información del producto como campos planos
 * (productId, productSku, productName) para simplificar el consumo desde Angular.
 *
 * La relación purchaseOrder no se incluye en el DTO de detalle porque los
 * detalles siempre se devuelven anidados dentro de PurchaseOrderResponseDTO
 * — incluir el id de la orden en cada detalle sería información redundante.
 */
@Mapper(componentModel = "spring")
public interface PurchaseOrderDetailMapper {

    /**
     * Convierte una entidad PurchaseOrderDetail a su DTO de salida.
     *
     * Los @Mapping con source multinivel navegan la relación @ManyToOne product
     * para extraer los campos del producto y aplanarlos en el DTO:
     *   product.id   → productId
     *   product.sku  → productSku
     *   product.name → productName
     *
     * Esto requiere que la sesión de Hibernate esté activa (transacción abierta)
     * para que pueda resolver el proxy LAZY de Product sin LazyInitializationException.
     *
     * @param detail entidad de detalle con su relación Product cargable
     * @return DTO listo para serializar como línea de detalle en la respuesta
     */
    @Mapping(source = "product.id",   target = "productId")
    @Mapping(source = "product.sku",  target = "productSku")
    @Mapping(source = "product.name", target = "productName")
    PurchaseOrderDetailResponseDTO toResponseDTO(PurchaseOrderDetail detail);

    /**
     * Convierte una lista de entidades a lista de DTOs de respuesta.
     * MapStruct aplica toResponseDTO() a cada elemento automáticamente.
     * Usado al serializar los detalles anidados dentro de PurchaseOrderResponseDTO.
     *
     * @param details lista de detalles de la orden
     * @return lista de DTOs de detalle
     */
    List<PurchaseOrderDetailResponseDTO> toResponseDTOList(List<PurchaseOrderDetail> details);

    /**
     * Convierte un PurchaseOrderDetailRequestDTO en una nueva entidad detalle.
     *
     * Campos ignorados y motivo:
     *   - id            : generado por PostgreSQL con IDENTITY
     *   - subtotal      : calculado por el servicio como quantity × unitPrice
     *   - product       : el servicio resuelve la entidad Product desde productId
     *   - purchaseOrder : el servicio asigna la orden padre antes de persistir
     *
     * El campo productId del DTO no tiene campo equivalente en la entidad
     * (la entidad tiene el objeto Product) — MapStruct lo ignora automáticamente
     * al no encontrar campo destino con ese nombre.
     *
     * @param dto datos del detalle enviados por el cliente
     * @return entidad PurchaseOrderDetail lista para que el servicio complete
     *         product, purchaseOrder y subtotal antes de persistir
     */
    @Mapping(target = "id",            ignore = true)
    @Mapping(target = "subtotal",      ignore = true)
    @Mapping(target = "product",       ignore = true)
    @Mapping(target = "purchaseOrder", ignore = true)
    PurchaseOrderDetail toEntity(PurchaseOrderDetailRequestDTO dto);
}
