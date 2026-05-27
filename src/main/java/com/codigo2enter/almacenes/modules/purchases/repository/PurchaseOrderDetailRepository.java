package com.codigo2enter.almacenes.modules.purchases.repository;

import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad PurchaseOrderDetail.
 *
 * Los detalles son inmutables una vez que la orden sale del estado PENDING.
 * Esta restricción se aplica en el servicio — el repositorio no tiene
 * conocimiento del estado de la orden padre.
 *
 * La eliminación física de detalles solo ocurre durante la construcción
 * de la orden (status == PENDING), a través del mecanismo orphanRemoval=true
 * en la relación @OneToMany de PurchaseOrder. El repositorio no expone
 * un método de eliminación explícito — la operación es gestionada por
 * Hibernate al remover el detalle de la lista order.getDetails().
 */
@Repository
public interface PurchaseOrderDetailRepository extends JpaRepository<PurchaseOrderDetail, Long> {

    /**
     * Recupera todos los detalles de una orden de compra específica.
     * Usado para consultar el contenido completo de una orden cuando
     * se necesita procesar cada línea individualmente (ej. en receiveOrder
     * para disparar registerStockMovement por cada detalle).
     *
     * @param purchaseOrderId ID de la orden de compra
     * @return lista de detalles de esa orden, vacía si no tiene ninguno
     */
    List<PurchaseOrderDetail> findByPurchaseOrderId(Long purchaseOrderId);

    /**
     * Recupera todos los detalles que referencian un producto específico.
     * Usado en findOrdersByProduct para construir el historial de compras
     * de un producto — qué órdenes lo incluyeron, a qué precio y en qué cantidad.
     *
     * @param productId ID del producto
     * @return lista de detalles que referencian ese producto
     */
    List<PurchaseOrderDetail> findByProductId(Long productId);

    /**
     * Verifica si un producto ya está incluido en una orden específica.
     * Usado en addDetail para evitar que el mismo producto aparezca en
     * dos líneas distintas de la misma orden — cada producto debe aparecer
     * solo una vez; si se necesita más cantidad, se edita la línea existente
     * con updateDetail.
     *
     * Genera SELECT COUNT en lugar de SELECT * — más eficiente para
     * validaciones de existencia sin necesitar el objeto completo.
     *
     * @param purchaseOrderId ID de la orden a verificar
     * @param productId       ID del producto a buscar
     * @return true si el producto ya está en la orden, false si está disponible
     */
    boolean existsByPurchaseOrderIdAndProductId(Long purchaseOrderId, Long productId);

    /**
     * Busca un detalle específico y valida en una sola query que pertenece
     * a la orden indicada en el path variable.
     *
     * Usado en updateDetail y removeDetail para:
     *   1. Encontrar el detalle por su id
     *   2. Verificar simultáneamente que pertenece a la orden del path
     *
     * Si el Optional es vacío, puede significar que el detailId no existe
     * O que existe pero pertenece a otra orden — en ambos casos el servicio
     * lanza RuntimeException con el mismo mensaje, evitando revelar si el
     * detalle existe en otra orden (seguridad por oscuridad).
     *
     * Reemplaza el patrón de dos queries separadas:
     *   findById(detailId) + validar detail.getPurchaseOrder().getId().equals(orderId)
     *
     * @param id              ID del detalle a buscar
     * @param purchaseOrderId ID de la orden a la que debe pertenecer
     * @return Optional con el detalle si existe y pertenece a esa orden, vacío si no
     */
    Optional<PurchaseOrderDetail> findByIdAndPurchaseOrderId(Long id, Long purchaseOrderId);
}
