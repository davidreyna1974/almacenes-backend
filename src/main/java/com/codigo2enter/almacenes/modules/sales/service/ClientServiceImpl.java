package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.sales.dto.ClientDTO;
import com.codigo2enter.almacenes.modules.sales.mapper.ClientMapper;
import com.codigo2enter.almacenes.modules.sales.model.Client;
import com.codigo2enter.almacenes.modules.sales.repository.ClientRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final UserRepository userRepository;
    private final ClientMapper clientMapper;

    @Override
    public ClientDTO createClient(ClientDTO dto) {
        if (dto.getRfc() != null && !dto.getRfc().isBlank()
                && clientRepository.existsByRfc(dto.getRfc())) {
            throw new RuntimeException(
                "Ya existe un cliente con el RFC '" + dto.getRfc() + "'.");
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()
                && clientRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException(
                "Ya existe un cliente con el email '" + dto.getEmail() + "'.");
        }

        Client client = clientMapper.toEntity(dto);
        client.setActive(true);
        client.setCreatedBy(resolveAuthenticatedUser());
        return clientMapper.toDTO(clientRepository.save(client));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientDTO> getAllActiveClients() {
        return clientMapper.toDTOList(clientRepository.findByActiveTrue());
    }

    /**
     * {@inheritDoc}
     *
     * Sort por name ASC para que la lista de clientes aparezca en orden
     * alfabético, coherente con la vista de lista sin paginación.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ClientDTO> getAllActiveClients(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Client> result = clientRepository.findByActiveTrue(pageable);
        return PageResponseDTO.from(result.map(clientMapper::toDTO));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ClientDTO> searchClients(String search, int page, int size) {
        String normalized = (search != null && !search.isBlank()) ? search.trim() : null;
        PageRequest pageable = PageRequest.of(page, size);
        Page<Client> result = clientRepository.searchClients(normalized, pageable);
        return PageResponseDTO.from(result.map(clientMapper::toDTO));
    }

    @Override
    @Transactional(readOnly = true)
    public ClientDTO findById(Long id) {
        return clientMapper.toDTO(findClientOrThrow(id));
    }

    @Override
    public ClientDTO updateClient(Long id, ClientDTO dto) {
        Client client = findClientOrThrow(id);

        if (dto.getRfc() != null && !dto.getRfc().isBlank()) {
            clientRepository.findByRfc(dto.getRfc()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new RuntimeException(
                        "El RFC '" + dto.getRfc() + "' ya está en uso por otro cliente.");
                }
            });
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            clientRepository.findByEmail(dto.getEmail()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new RuntimeException(
                        "El email '" + dto.getEmail() + "' ya está en uso por otro cliente.");
                }
            });
        }

        clientMapper.updateFromDTO(dto, client);
        client.setUpdatedAt(LocalDateTime.now());
        client.setUpdatedBy(resolveAuthenticatedUser());
        return clientMapper.toDTO(clientRepository.save(client));
    }

    @Override
    public void deactivateClient(Long id) {
        Client client = findClientOrThrow(id);
        if (!saleOrderRepository.findActiveOrdersByClient(id).isEmpty()) {
            throw new RuntimeException(
                "El cliente tiene órdenes de venta activas (PENDING o APPROVED) y no puede desactivarse.");
        }
        client.setActive(false);
        client.setUpdatedAt(LocalDateTime.now());
        client.setUpdatedBy(resolveAuthenticatedUser());
    }

    private Client findClientOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "Cliente con id " + id + " no encontrado."));
    }

    private User resolveAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(
                    "Usuario autenticado no encontrado en el sistema."));
    }
}
