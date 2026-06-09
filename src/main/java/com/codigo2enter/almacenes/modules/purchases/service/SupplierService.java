package com.codigo2enter.almacenes.modules.purchases.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.SupplierDTO;

import java.util.List;

/**
 * Contrato de la capa de servicio para la gestión de proveedores.
 *
 * Define las operaciones de negocio disponibles para el controlador REST.
 * La implementación concreta vive en SupplierServiceImpl, desacoplando
 * la interfaz del detalle técnico (JPA, validaciones, transacciones).
 */
public interface SupplierService {

    /**
     * Registra un nuevo proveedor en el sistema.
     * Valida unicidad de RFC y razón social antes de persistir.
     *
     * @param dto datos del nuevo proveedor enviados por el cliente
     * @return SupplierDTO con el id asignado por la base de datos
     */
    SupplierDTO createSupplier(SupplierDTO dto);

    /**
     * Retorna todos los proveedores activos (active = true).
     * Usado por Angular para poblar selectores al crear una orden de compra.
     *
     * @return lista de proveedores vigentes, vacía si no hay ninguno
     */
    List<SupplierDTO> getAllActiveSuppliers();

    /**
     * Retorna una página de proveedores activos, ordenados por razón social ascendente.
     *
     * @param page número de página (base 0)
     * @param size cantidad de registros por página
     * @return PageResponseDTO con los proveedores de la página solicitada
     */
    PageResponseDTO<SupplierDTO> getAllActiveSuppliers(int page, int size);

    /**
     * Búsqueda paginada de proveedores activos con filtro de texto opcional.
     * La búsqueda es insensible a mayúsculas y acentos (f_unaccent en PostgreSQL).
     * Busca en company_name y rfc simultáneamente.
     *
     * @param search   texto a buscar; null retorna todos los activos
     * @param page     número de página (base 0)
     * @param size     cantidad de registros por página
     * @return PageResponseDTO con los proveedores que coinciden
     */
    PageResponseDTO<SupplierDTO> searchSuppliers(String search, int page, int size);

    /**
     * Busca un proveedor por su identificador.
     *
     * @param id identificador del proveedor
     * @return SupplierDTO con los datos del proveedor
     */
    SupplierDTO findById(Long id);

    /**
     * Actualiza los datos editables de un proveedor existente.
     * Valida unicidad de RFC y razón social excluyendo al proveedor que se edita.
     *
     * @param id  identificador del proveedor a modificar
     * @param dto datos nuevos enviados por el cliente
     * @return SupplierDTO con los datos actualizados
     */
    SupplierDTO updateSupplier(Long id, SupplierDTO dto);

    /**
     * Desactiva lógicamente un proveedor (soft delete: active = false).
     * No permite la desactivación si el proveedor tiene órdenes PENDING o APPROVED.
     *
     * @param id identificador del proveedor a desactivar
     */
    void deactivateSupplier(Long id);
}
