package com.codigo2enter.almacenes.modules.inventory.repository;

import com.codigo2enter.almacenes.modules.inventory.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de persistencia para la entidad StockMovement.
 *
 * Los movimientos de stock son registros inmutables — nunca se actualizan ni
 * eliminan. Este repositorio solo necesita operaciones de escritura (heredadas
 * de JpaRepository) y de consulta para construir el historial del Kardex.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Recupera el historial completo de movimientos de un producto, ordenado
     * del más reciente al más antiguo (ORDER BY created_at DESC).
     *
     * Este orden es el esperado por el componente de Kardex en Angular:
     * el movimiento más reciente aparece en la primera fila de la tabla,
     * facilitando la revisión de las últimas operaciones sin necesidad
     * de desplazarse hasta el final del listado.
     *
     * @param productId ID del producto cuyo historial se consulta
     * @return lista de movimientos ordenados de más reciente a más antiguo
     */
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
}
