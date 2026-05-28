package com.codigo2enter.almacenes.modules.purchases.service;

import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderDetailUpdateRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderRequestDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderResponseDTO;
import com.codigo2enter.almacenes.modules.purchases.dto.PurchaseOrderUpdateRequestDTO;

import java.util.List;

/**
 * Contrato de la capa de servicio para la gestión de órdenes de compra.
 *
 * Centraliza la lógica de negocio del ciclo de vida de una orden:
 * creación, edición, transiciones de estado y gestión de detalles.
 * La integración con el inventario ocurre en receiveOrder, donde cada
 * detalle dispara un movimiento de stock tipo IN.
 */
public interface PurchaseOrderService {

    /**
     * Crea una nueva orden de compra en estado PENDING.
     * Genera el orderNumber, resuelve el usuario autenticado como createdBy,
     * procesa los detalles y calcula el totalAmount.
     *
     * @param dto datos de la orden y sus detalles enviados por el cliente
     * @return PurchaseOrderResponseDTO con la orden completa persistida
     */
    PurchaseOrderResponseDTO createOrder(PurchaseOrderRequestDTO dto);

    /**
     * Busca una orden por su identificador.
     *
     * @param id identificador de la orden
     * @return PurchaseOrderResponseDTO con la orden y sus detalles
     */
    PurchaseOrderResponseDTO findById(Long id);

    /**
     * Retorna todas las órdenes en el estado indicado.
     * El status se recibe como String y el servicio lo convierte al enum.
     *
     * @param status nombre del estado ("PENDING", "APPROVED", "RECEIVED", "CANCELLED")
     * @return lista de órdenes en ese estado
     */
    List<PurchaseOrderResponseDTO> findByStatus(String status);

    /**
     * Retorna todas las órdenes de un proveedor específico.
     *
     * @param supplierId identificador del proveedor
     * @return lista de órdenes del proveedor en todos los estados
     */
    List<PurchaseOrderResponseDTO> findBySupplierId(Long supplierId);

    /**
     * Retorna las órdenes de un proveedor filtradas por estado.
     * Combina ambos filtros en una sola consulta eficiente al repositorio,
     * evitando que el cliente tenga que pedir todas las órdenes del proveedor
     * y filtrarlas en el frontend.
     *
     * Aplica las mismas validaciones que findBySupplierId (proveedor debe existir)
     * y findByStatus (status debe ser un valor válido del enum).
     *
     * @param supplierId identificador del proveedor
     * @param status     nombre del estado ("PENDING", "APPROVED", "RECEIVED", "CANCELLED")
     * @return lista de órdenes del proveedor en ese estado, vacía si no hay ninguna
     */
    List<PurchaseOrderResponseDTO> findBySupplierIdAndStatus(Long supplierId, String status);

    /**
     * Retorna todas las órdenes que contienen un producto específico.
     *
     * @param productId identificador del producto
     * @return lista de órdenes que incluyen ese producto en sus detalles
     */
    List<PurchaseOrderResponseDTO> findOrdersByProduct(Long productId);

    /**
     * Actualiza los campos editables de una orden (notes y supplierId).
     * Solo permitido cuando status == PENDING.
     *
     * @param id  identificador de la orden
     * @param dto datos actualizados (supplierId y notes)
     * @return PurchaseOrderResponseDTO con los datos actualizados
     */
    PurchaseOrderResponseDTO updateOrder(Long id, PurchaseOrderUpdateRequestDTO dto);

    /**
     * Transiciona la orden de PENDING a APPROVED.
     * Valida que tenga al menos un detalle antes de aprobar.
     *
     * @param id identificador de la orden a aprobar
     * @return PurchaseOrderResponseDTO con status APPROVED y approvedAt asignado
     */
    PurchaseOrderResponseDTO approveOrder(Long id);

    /**
     * Transiciona la orden de APPROVED a RECEIVED.
     * Dispara registerStockMovement(IN) por cada detalle de la orden,
     * incrementando el stock de cada producto automáticamente.
     * Toda la operación ocurre en una sola transacción.
     *
     * @param id identificador de la orden a recibir
     * @return PurchaseOrderResponseDTO con status RECEIVED y receivedAt asignado
     */
    PurchaseOrderResponseDTO receiveOrder(Long id);

    /**
     * Transiciona la orden de PENDING o APPROVED a CANCELLED.
     * No impacta el inventario en ningún caso.
     *
     * @param id identificador de la orden a cancelar
     * @return PurchaseOrderResponseDTO con status CANCELLED y cancelledAt asignado
     */
    PurchaseOrderResponseDTO cancelOrder(Long id);

    /**
     * Agrega una línea de detalle a una orden PENDING.
     * Valida que el producto no esté ya en la orden y calcula el subtotal.
     * Recalcula el totalAmount de la orden.
     *
     * @param orderId identificador de la orden
     * @param dto     datos del detalle (productId, quantity, unitPrice)
     * @return PurchaseOrderResponseDTO completo con el nuevo detalle y totalAmount actualizado
     */
    PurchaseOrderResponseDTO addDetail(Long orderId, PurchaseOrderDetailRequestDTO dto);

    /**
     * Actualiza quantity y unitPrice de un detalle existente en una orden PENDING.
     * Recalcula subtotal del detalle y totalAmount de la orden.
     *
     * @param orderId  identificador de la orden
     * @param detailId identificador del detalle a actualizar
     * @param dto      nuevos valores de quantity y unitPrice
     * @return PurchaseOrderResponseDTO completo con los totales recalculados
     */
    PurchaseOrderResponseDTO updateDetail(Long orderId, Long detailId,
                                          PurchaseOrderDetailUpdateRequestDTO dto);

    /**
     * Elimina un detalle de una orden PENDING.
     * Recalcula el totalAmount de la orden tras la eliminación.
     *
     * @param orderId  identificador de la orden
     * @param detailId identificador del detalle a eliminar
     */
    void removeDetail(Long orderId, Long detailId);
}
