package com.codigo2enter.almacenes.modules.inventory.mapper;

import com.codigo2enter.almacenes.modules.inventory.dto.StockMovementResponseDTO;
import com.codigo2enter.almacenes.modules.inventory.model.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct para la conversión de StockMovement a su DTO de salida.
 *
 * Los movimientos de stock son registros inmutables (solo se crean, nunca se
 * modifican ni eliminan), por lo que este mapper solo necesita la dirección
 * entidad → DTO. No existe toEntity() porque la creación de un movimiento
 * se realiza directamente en ProductServiceImpl, que construye la entidad
 * con los datos validados por el servicio.
 *
 * Dos conversiones especiales manejadas por @Mapping:
 *   1. product.id / product.name → productId / productName
 *      (aplanar la relación @ManyToOne en campos simples del DTO)
 *   2. type (enum MovementType) → type (String)
 *      MapStruct llama a MovementType.name() automáticamente al detectar
 *      que el destino es String, produciendo "IN" u "OUT" directamente.
 */
@Mapper(componentModel = "spring")
public interface StockMovementMapper {

    /**
     * Convierte una entidad StockMovement a su DTO de salida.
     *
     * @Mapping con source = "product.id" y source = "product.name" navega
     * la relación LAZY para extraer el ID y nombre del producto relacionado.
     * Al igual que en ProductMapper, esto requiere una transacción activa
     * para que Hibernate resuelva el proxy LAZY sin error.
     *
     * El campo 'type' se convierte de MovementType (enum) a String
     * automáticamente por MapStruct — no requiere @Mapping explícito
     * porque ambos campos se llaman igual y MapStruct aplica Enum.name()
     * al detectar el tipo de destino String.
     *
     * @param movement entidad de movimiento con su relación Product cargada
     * @return DTO listo para serializar en el historial del Kardex
     */
    @Mapping(source = "product.id",   target = "productId")
    @Mapping(source = "product.name", target = "productName")
    StockMovementResponseDTO toResponseDTO(StockMovement movement);

    /**
     * Convierte una lista de movimientos a lista de DTOs de salida.
     * Usado en el endpoint GET /products/{id}/movements para devolver
     * el historial completo del Kardex ya ordenado por el repositorio.
     *
     * @param movements lista de movimientos ordenados por fecha descendente
     * @return lista de DTOs lista para serializar como JSON array
     */
    List<StockMovementResponseDTO> toResponseDTOList(List<StockMovement> movements);
}
