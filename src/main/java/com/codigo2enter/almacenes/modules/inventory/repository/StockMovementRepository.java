package com.codigo2enter.almacenes.modules.inventory.repository;

import com.codigo2enter.almacenes.modules.inventory.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de persistencia para la entidad StockMovement.
 *
 * Los movimientos de stock son registros inmutables — nunca se actualizan ni
 * eliminan. Este repositorio solo necesita operaciones de escritura (heredadas
 * de JpaRepository) y de consulta para construir el historial del Kardex.
 *
 * Adicionalmente contiene queries para el módulo reports: Kardex por período
 * y totales de entradas/salidas para el resumen de movimientos.
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

    /**
     * Versión paginada del Kardex de un producto.
     * El sort en el nombre del método (OrderByCreatedAtDesc) se combina con
     * el Pageable — Spring Data aplica el ORDER BY del método antes que cualquier
     * sort del Pageable. Se pasa Pageable.ofSize(n) o PageRequest sin Sort
     * para dejar activo el orden DESC del nombre de método.
     *
     * @param productId ID del producto cuyo historial se consulta
     * @param pageable  parámetros de paginación (el sort se ignora — el método ya ordena)
     * @return página de movimientos ordenados del más reciente al más antiguo
     */
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    // ── QUERIES ANALÍTICAS PARA EL MÓDULO REPORTS ──────────────────────────

    /**
     * Movimientos de un producto en un período, ordenados cronológicamente ASC.
     * El orden ASC es clave para el Kardex: el servicio itera los movimientos
     * en orden cronológico para acumular el saldo correctamente (primero el más
     * antiguo, que determina el saldo de arranque del período visible).
     *
     * @param productId ID del producto
     * @param from      inicio del período (inclusivo)
     * @param to        fin del período (exclusivo, día siguiente a las 00:00:00)
     * @return lista de movimientos ordenada ASC por createdAt
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.product.id = :productId " +
           "AND sm.createdAt >= :from AND sm.createdAt < :to ORDER BY sm.createdAt ASC")
    List<StockMovement> findByProductAndPeriod(@Param("productId") Long productId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Total de unidades que ingresaron al almacén en el período (movimientos IN).
     * COALESCE garantiza Integer 0 en lugar de null cuando no hay movimientos IN.
     * Usado en el reporte de resumen de movimientos y para calcular openingStock en Kardex.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return suma de cantidades de movimientos IN, nunca null
     */
    @Query("SELECT COALESCE(SUM(sm.quantity), 0) FROM StockMovement sm " +
           "WHERE sm.type = com.codigo2enter.almacenes.modules.inventory.model.MovementType.IN " +
           "AND sm.createdAt >= :from AND sm.createdAt < :to")
    Integer sumInByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Total de unidades que salieron del almacén en el período (movimientos OUT).
     * COALESCE garantiza Integer 0 en lugar de null cuando no hay movimientos OUT.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return suma de cantidades de movimientos OUT, nunca null
     */
    @Query("SELECT COALESCE(SUM(sm.quantity), 0) FROM StockMovement sm " +
           "WHERE sm.type = com.codigo2enter.almacenes.modules.inventory.model.MovementType.OUT " +
           "AND sm.createdAt >= :from AND sm.createdAt < :to")
    Integer sumOutByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
