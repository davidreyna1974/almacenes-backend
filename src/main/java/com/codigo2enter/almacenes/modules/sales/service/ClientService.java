package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.modules.sales.dto.ClientDTO;

import java.util.List;

public interface ClientService {
    ClientDTO createClient(ClientDTO dto);
    List<ClientDTO> getAllActiveClients();
    ClientDTO findById(Long id);
    ClientDTO updateClient(Long id, ClientDTO dto);
    void deactivateClient(Long id);
}
