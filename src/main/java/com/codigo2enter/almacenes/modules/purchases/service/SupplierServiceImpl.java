package com.codigo2enter.almacenes.modules.purchases.service;

import com.codigo2enter.almacenes.modules.purchases.dto.SupplierDTO;
import com.codigo2enter.almacenes.modules.purchases.mapper.SupplierMapper;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderRepository;
import com.codigo2enter.almacenes.modules.purchases.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación concreta de SupplierService.
 *
 * Gestiona el ciclo de vida de los proveedores con soft delete — nunca se
 * eliminan físicamente para preservar la integridad referencial con las
 * órdenes de compra históricas que los referencian.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierMapper supplierMapper;

    /**
     * {@inheritDoc}
     *
     * Valida RFC y razón social únicos antes de persistir.
     * El campo 'active' se inicializa en true por @Builder.Default en la entidad.
     */
    @Override
    public SupplierDTO createSupplier(SupplierDTO dto) {
        if (supplierRepository.existsByRfc(dto.getRfc())) {
            throw new RuntimeException(
                    "Ya existe un proveedor con el RFC '" + dto.getRfc() + "'.");
        }
        if (supplierRepository.existsByCompanyName(dto.getCompanyName())) {
            throw new RuntimeException(
                    "Ya existe un proveedor con la razón social '" + dto.getCompanyName() + "'.");
        }

        Supplier saved = supplierRepository.save(supplierMapper.toEntity(dto));
        return supplierMapper.toDTO(saved);
    }

    /**
     * {@inheritDoc}
     *
     * readOnly = true optimiza la sesión de Hibernate — omite el flush al
     * cerrar la transacción al ser una operación de solo lectura.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SupplierDTO> getAllActiveSuppliers() {
        return supplierMapper.toDTOList(supplierRepository.findByActiveTrue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public SupplierDTO findById(Long id) {
        return supplierMapper.toDTO(findSupplierOrThrow(id));
    }

    /**
     * {@inheritDoc}
     *
     * Valida que el RFC y la razón social nuevos no pertenezcan a otro proveedor distinto.
     * La condición !existing.getId().equals(id) permite que el proveedor conserve
     * sus propios valores sin disparar el error de duplicado.
     * Hibernate dirty-checking persiste los cambios al cerrar la transacción
     * sin necesidad de llamar explícitamente a save().
     */
    @Override
    public SupplierDTO updateSupplier(Long id, SupplierDTO dto) {
        Supplier supplier = findSupplierOrThrow(id);

        supplierRepository.findByRfc(dto.getRfc()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException(
                        "El RFC '" + dto.getRfc() + "' ya está en uso por otro proveedor.");
            }
        });

        supplierRepository.findByCompanyName(dto.getCompanyName()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException(
                        "La razón social '" + dto.getCompanyName() + "' ya está en uso por otro proveedor.");
            }
        });

        supplier.setRfc(dto.getRfc());
        supplier.setCompanyName(dto.getCompanyName());
        supplier.setContactName(dto.getContactName());
        supplier.setPhone(dto.getPhone());
        supplier.setEmail(dto.getEmail());
        supplier.setAddress(dto.getAddress());

        return supplierMapper.toDTO(supplier);
    }

    /**
     * {@inheritDoc}
     *
     * Bloquea la desactivación si el proveedor tiene órdenes PENDING o APPROVED
     * — deben cancelarse o recibirse antes de dar de baja al proveedor.
     * Hibernate dirty-checking persiste el cambio de active sin llamar a save().
     */
    @Override
    public void deactivateSupplier(Long id) {
        Supplier supplier = findSupplierOrThrow(id);

        if (!purchaseOrderRepository.findActiveOrdersBySupplier(id).isEmpty()) {
            throw new RuntimeException(
                    "No se puede desactivar el proveedor: tiene órdenes de compra " +
                    "en estado PENDING o APPROVED. Cancélelas o recíbalas primero.");
        }

        supplier.setActive(false);
    }

    // -------------------------------------------------------------------------
    // Métodos privados de apoyo
    // -------------------------------------------------------------------------

    /**
     * Busca un proveedor por ID o lanza RuntimeException si no existe.
     * Centraliza el manejo del "not found" para no repetirlo en cada método.
     */
    private Supplier findSupplierOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Proveedor con id " + id + " no encontrado."));
    }
}
