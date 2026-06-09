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

    /**
     * Búsqueda paginada de clientes activos con filtro de texto opcional.
     * La búsqueda es insensible a mayúsculas y acentos (f_unaccent en PostgreSQL).
     * Busca en name, rfc y contact_name simultáneamente.
     *
     * @param search   texto a buscar; null retorna todos los activos
     * @param page     número de página (base 0)
     * @param size     cantidad de registros por página
     * @return PageResponseDTO con los clientes que coinciden
     */
    PageResponseDTO<ClientDTO> searchClients(String search, int page, int size);

    ClientDTO findById(Long id);
    ClientDTO updateClient(Long id, ClientDTO dto);
    void deactivateClient(Long id);
}
