package com.codigo2enter.almacenes.modules.sales.repository;

import com.codigo2enter.almacenes.modules.sales.model.SaleOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad SaleOrderDetail.
 *
 * Dos métodos clave de seguridad:
 *
 * existsBySaleOrderIdAndProductId — evita que el mismo producto aparezca dos
 * veces en una misma orden. Si el cliente quiere cambiar la cantidad, debe usar
 * updateDetail, no agregar un segundo detalle del mismo producto.
 *
 * findByIdAndSaleOrderId — valida que el detalle que se quiere modificar o
 * eliminar pertenece efectivamente a la orden indicada en la URL, previniendo
 * accesos cruzados entre órdenes distintas.
 */
@Repository
public interface SaleOrderDetailRepository extends JpaRepository<SaleOrderDetail, Long> {

    /**
     * Verifica si ya existe un detalle con ese producto en la orden indicada.
     * Usado en addDetail() para prevenir duplicados dentro de la misma orden.
     */
    boolean existsBySaleOrderIdAndProductId(Long saleOrderId, Long productId);

    /**
     * Busca un detalle por su ID validando que pertenezca a la orden indicada.
     * Previene que un cliente pueda modificar detalles de otras órdenes
     * manipulando el detailId en la URL.
     */
    Optional<SaleOrderDetail> findByIdAndSaleOrderId(Long id, Long saleOrderId);
}
