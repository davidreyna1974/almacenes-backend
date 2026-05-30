package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.modules.sales.dto.*;

import java.util.List;

public interface SaleOrderService {
    SaleOrderResponseDTO createOrder(SaleOrderRequestDTO dto);
    SaleOrderResponseDTO findById(Long id);
    List<SaleOrderResponseDTO> findByStatus(String status);
    List<SaleOrderResponseDTO> findByClientId(Long clientId);
    List<SaleOrderResponseDTO> findByClientIdAndStatus(Long clientId, String status);
    List<SaleOrderResponseDTO> findByProductId(Long productId);
    List<SaleOrderResponseDTO> findByProductIdAndStatus(Long productId, String status);
    SaleOrderResponseDTO updateOrder(Long id, SaleOrderUpdateRequestDTO dto);
    SaleOrderResponseDTO approveOrder(Long id);
    SaleOrderResponseDTO deliverOrder(Long id);
    SaleOrderResponseDTO cancelOrder(Long id);
    SaleOrderResponseDTO addDetail(Long orderId, SaleOrderDetailRequestDTO dto);
    SaleOrderResponseDTO updateDetail(Long orderId, Long detailId, SaleOrderDetailUpdateRequestDTO dto);
    void removeDetail(Long orderId, Long detailId);
}
