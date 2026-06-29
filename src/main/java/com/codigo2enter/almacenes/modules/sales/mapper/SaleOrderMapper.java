package com.codigo2enter.almacenes.modules.sales.mapper;

import com.codigo2enter.almacenes.modules.sales.dto.SaleOrderRequestDTO;
import com.codigo2enter.almacenes.modules.sales.dto.SaleOrderResponseDTO;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {SaleOrderDetailMapper.class})
public interface SaleOrderMapper {

    @Mapping(source = "client.id",            target = "clientId")
    @Mapping(source = "client.name",          target = "clientName")
    @Mapping(source = "createdBy.id",         target = "createdById")
    @Mapping(source = "createdBy.username",   target = "createdByUsername")
    @Mapping(source = "updatedBy.id",         target = "updatedById")
    @Mapping(source = "updatedBy.username",   target = "updatedByUsername")
    @Mapping(source = "approvedBy.id",        target = "approvedById")
    @Mapping(source = "approvedBy.username",  target = "approvedByUsername")
    @Mapping(source = "deliveredBy.id",       target = "deliveredById")
    @Mapping(source = "deliveredBy.username", target = "deliveredByUsername")
    @Mapping(source = "cancelledBy.id",       target = "cancelledById")
    @Mapping(source = "cancelledBy.username", target = "cancelledByUsername")
    @Mapping(source = "status",               target = "status", qualifiedByName = "statusToString")
    SaleOrderResponseDTO toResponseDTO(SaleOrder order);

    List<SaleOrderResponseDTO> toResponseDTOList(List<SaleOrder> orders);

    /**
     * El servicio construye la SaleOrder completa — el mapper solo mapea
     * los campos que el cliente puede enviar (notes). Todo lo demás es ignorado
     * porque es gestionado por el servicio o por el sistema.
     */
    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "status",      ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "client",      ignore = true)
    @Mapping(target = "createdBy",   ignore = true)
    @Mapping(target = "details",     ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   ignore = true)
    @Mapping(target = "updatedBy",   ignore = true)
    @Mapping(target = "approvedAt",  ignore = true)
    @Mapping(target = "approvedBy",  ignore = true)
    @Mapping(target = "deliveredAt", ignore = true)
    @Mapping(target = "deliveredBy", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    SaleOrder toEntity(SaleOrderRequestDTO dto);

    @org.mapstruct.Named("statusToString")
    default String statusToString(com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus status) {
        return status == null ? null : status.name();
    }
}
