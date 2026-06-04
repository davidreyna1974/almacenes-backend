package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.sales.dto.ClientDTO;

import java.util.List;

public interface ClientService {
    ClientDTO createClient(ClientDTO dto);
    List<ClientDTO> getAllActiveClients();

    /**
     * Retorna una página de clientes activos, ordenados por nombre ascendente.
     *
     * @param page número de página (base 0)
     * @param size cantidad de registros por página
     * @return PageResponseDTO con los clientes de la página solicitada
     */
    PageResponseDTO<ClientDTO> getAllActiveClients(int page, int size);

    ClientDTO findById(Long id);
    ClientDTO updateClient(Long id, ClientDTO dto);
    void deactivateClient(Long id);
}
