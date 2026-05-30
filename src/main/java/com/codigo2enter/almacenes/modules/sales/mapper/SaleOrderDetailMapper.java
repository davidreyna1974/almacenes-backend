package com.codigo2enter.almacenes.modules.sales.mapper;

import com.codigo2enter.almacenes.modules.sales.dto.SaleOrderDetailRequestDTO;
import com.codigo2enter.almacenes.modules.sales.dto.SaleOrderDetailResponseDTO;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SaleOrderDetailMapper {

    @Mapping(source = "product.id",   target = "productId")
    @Mapping(source = "product.sku",  target = "productSku")
    @Mapping(source = "product.name", target = "productName")
    SaleOrderDetailResponseDTO toResponseDTO(SaleOrderDetail detail);

    List<SaleOrderDetailResponseDTO> toResponseDTOList(List<SaleOrderDetail> details);

    /**
     * unitCost ignorado — el servicio lo asigna leyendo Product.unitCost.
     * Si el mapper lo leyera del DTO siempre sería null (el DTO no tiene ese campo).
     * subtotal ignorado — calculado por el servicio como unitPrice × quantity.
     * saleOrder y product ignorados — relaciones resueltas por el servicio.
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "unitCost",  ignore = true)
    @Mapping(target = "subtotal",  ignore = true)
    @Mapping(target = "saleOrder", ignore = true)
    @Mapping(target = "product",   ignore = true)
    SaleOrderDetail toEntity(SaleOrderDetailRequestDTO dto);
}
