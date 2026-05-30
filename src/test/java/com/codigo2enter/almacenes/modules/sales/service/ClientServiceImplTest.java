package com.codigo2enter.almacenes.modules.sales.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.sales.dto.ClientDTO;
import com.codigo2enter.almacenes.modules.sales.mapper.ClientMapper;
import com.codigo2enter.almacenes.modules.sales.model.Client;
import com.codigo2enter.almacenes.modules.sales.repository.ClientRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock ClientRepository clientRepository;
    @Mock SaleOrderRepository saleOrderRepository;
    @Mock UserRepository userRepository;
    @Mock ClientMapper clientMapper;
    @InjectMocks ClientServiceImpl clientService;

    private User user;
    private Client client;
    private ClientDTO clientDTO;

    @BeforeEach
    void setUp() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("tester01");
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        user = User.builder().id(1L).username("tester01").password("hash").build();
        lenient().when(userRepository.findByUsername("tester01")).thenReturn(Optional.of(user));

        client = Client.builder().id(1L).name("Comercial Reyes SA").active(true).build();
        clientDTO = ClientDTO.builder().id(1L).name("Comercial Reyes SA").active(true).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createClient_sinRfcNiEmail_debePersistir() {
        ClientDTO input = ClientDTO.builder().name("Nuevo Cliente").build();
        when(clientMapper.toEntity(input)).thenReturn(client);
        when(clientRepository.save(any())).thenReturn(client);
        when(clientMapper.toDTO(client)).thenReturn(clientDTO);

        ClientDTO result = clientService.createClient(input);

        assertNotNull(result);
        verify(clientRepository).save(any());
    }

    @Test
    void createClient_rfcDuplicado_debeLanzarExcepcion() {
        ClientDTO input = ClientDTO.builder().name("X").rfc("ABC123456789").build();
        when(clientRepository.existsByRfc("ABC123456789")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> clientService.createClient(input));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void createClient_emailDuplicado_debeLanzarExcepcion() {
        ClientDTO input = ClientDTO.builder().name("X").email("dup@test.com").build();
        when(clientRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> clientService.createClient(input));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void getAllActiveClients_debeRetornarLista() {
        when(clientRepository.findByActiveTrue()).thenReturn(List.of(client));
        when(clientMapper.toDTOList(any())).thenReturn(List.of(clientDTO));

        List<ClientDTO> result = clientService.getAllActiveClients();

        assertEquals(1, result.size());
    }

    @Test
    void findById_existente_debeRetornarDTO() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientMapper.toDTO(client)).thenReturn(clientDTO);

        ClientDTO result = clientService.findById(1L);

        assertEquals("Comercial Reyes SA", result.getName());
    }

    @Test
    void findById_noExistente_debeLanzarExcepcion() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> clientService.findById(99L));
    }

    @Test
    void updateClient_rfcEnUso_debeLanzarExcepcion() {
        Client otro = Client.builder().id(2L).rfc("ABC123456789").build();
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientRepository.findByRfc("ABC123456789")).thenReturn(Optional.of(otro));

        ClientDTO input = ClientDTO.builder().name("X").rfc("ABC123456789").build();

        assertThrows(RuntimeException.class, () -> clientService.updateClient(1L, input));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void updateClient_mismoRfc_debePermitir() {
        client.setRfc("ABC123456789");
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(clientRepository.findByRfc("ABC123456789")).thenReturn(Optional.of(client));
        when(clientRepository.save(any())).thenReturn(client);
        when(clientMapper.toDTO(client)).thenReturn(clientDTO);

        ClientDTO input = ClientDTO.builder().name("Nuevo nombre").rfc("ABC123456789").build();
        assertDoesNotThrow(() -> clientService.updateClient(1L, input));
    }

    @Test
    void deactivateClient_sinOrdenesActivas_debeDesactivar() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(saleOrderRepository.findActiveOrdersByClient(1L)).thenReturn(List.of());

        clientService.deactivateClient(1L);

        assertFalse(client.isActive());
    }

    @Test
    void deactivateClient_conOrdenesActivas_debeLanzarExcepcion() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(saleOrderRepository.findActiveOrdersByClient(1L))
                .thenReturn(List.of(mock(com.codigo2enter.almacenes.modules.sales.model.SaleOrder.class)));

        assertThrows(RuntimeException.class, () -> clientService.deactivateClient(1L));
        assertTrue(client.isActive());
    }

    @Test
    void deactivateClient_noExistente_debeLanzarExcepcion() {
        when(clientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> clientService.deactivateClient(99L));
    }
}
