package com.codigo2enter.almacenes.modules.purchases.mapper;

import com.codigo2enter.almacenes.modules.purchases.dto.SupplierDTO;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct para la conversión entre Supplier y SupplierDTO.
 *
 * SupplierDTO es un DTO unificado (request y response), por lo que este
 * mapper cubre dos direcciones:
 *   - Supplier  → SupplierDTO  (para respuestas HTTP)
 *   - SupplierDTO → Supplier   (para crear una nueva entidad)
 *
 * Los campos rfc, companyName, contactName, phone, email y address se mapean
 * automáticamente por coincidencia de nombre entre DTO y entidad — no requieren
 * @Mapping explícito.
 *
 * No existe updateFromDTO porque SupplierServiceImpl actualiza los campos
 * directamente sobre la entidad usando dirty-checking de Hibernate.
 */
@Mapper(componentModel = "spring")
public interface SupplierMapper {

    /**
     * Convierte una entidad Supplier a su DTO de salida.
     * Los campos de auditoría de usuario se aplanan: createdBy y updatedBy
     * (relaciones @ManyToOne) se exponen como pares de ID + username.
     *
     * @param supplier entidad persistida con datos de la base de datos
     * @return SupplierDTO listo para serializar como JSON en la respuesta HTTP
     */
    @Mapping(source = "createdBy.id",       target = "createdById")
    @Mapping(source = "createdBy.username", target = "createdByUsername")
    @Mapping(source = "updatedBy.id",       target = "updatedById")
    @Mapping(source = "updatedBy.username", target = "updatedByUsername")
    SupplierDTO toDTO(Supplier supplier);

    /**
     * Convierte una lista de entidades Supplier a una lista de SupplierDTO.
     * MapStruct genera esta implementación aplicando toDTO() a cada elemento.
     * Usado en getAllActiveSuppliers para devolver el catálogo de proveedores.
     *
     * @param suppliers lista de entidades recuperadas de la base de datos
     * @return lista de DTOs lista para serializar como JSON array
     */
    List<SupplierDTO> toDTOList(List<Supplier> suppliers);

    /**
     * Convierte un SupplierDTO de entrada en una nueva entidad Supplier.
     *
     * Campos ignorados:
     *   - id        : generado por PostgreSQL con IDENTITY, no viene del cliente
     *   - active    : el servicio lo inicializa en true vía @Builder.Default
     *   - createdAt : el servicio lo inicializa con @Builder.Default = now()
     *
     * @param dto datos del proveedor enviados por el cliente
     * @return entidad Supplier lista para que el servicio persista con save()
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "active",    ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Supplier toEntity(SupplierDTO dto);
}
