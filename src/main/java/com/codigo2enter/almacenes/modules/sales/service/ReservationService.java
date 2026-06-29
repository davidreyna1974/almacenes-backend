package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.modules.sales.dto.*;

import java.util.List;

public interface ReservationService {
    ReservationSummaryDTO getSummary();
    List<ReservedProductDTO> getReservedProducts();
    ReservedProductDTO getProductReservationDetail(Long productId);
    List<ReservedClientDTO> getClientsWithReservations();
    ReservedClientDTO getClientReservationDetail(Long clientId);
}
