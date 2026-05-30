package com.codigo2enter.almacenes.modules.sales.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.sales.dto.ClientDTO;
import com.codigo2enter.almacenes.modules.sales.service.ClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClientControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ClientService clientService;
    @MockBean JwtUtils jwtUtils;

    private static final String BASE = "/api/v1/sales/clients";

    @Test
    void createClient_bodyValido_retorna201() throws Exception {
        ClientDTO dto = ClientDTO.builder().name("Comercial Reyes SA").build();
        ClientDTO response = ClientDTO.builder().id(1L).name("Comercial Reyes SA").active(true).build();
        when(clientService.createClient(any())).thenReturn(response);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Comercial Reyes SA"));
    }

    @Test
    void createClient_sinNombre_retorna400() throws Exception {
        ClientDTO dto = ClientDTO.builder().build();

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllActiveClients_retorna200() throws Exception {
        ClientDTO dto = ClientDTO.builder().id(1L).name("Comercial Reyes SA").build();
        when(clientService.getAllActiveClients()).thenReturn(List.of(dto));

        mockMvc.perform(get(BASE + "/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Comercial Reyes SA"));
    }

    @Test
    void findById_retorna200() throws Exception {
        ClientDTO dto = ClientDTO.builder().id(1L).name("Comercial Reyes SA").build();
        when(clientService.findById(1L)).thenReturn(dto);

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateClient_retorna200() throws Exception {
        ClientDTO input = ClientDTO.builder().name("Nuevo Nombre SA").build();
        ClientDTO response = ClientDTO.builder().id(1L).name("Nuevo Nombre SA").build();
        when(clientService.updateClient(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put(BASE + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nuevo Nombre SA"));
    }

    @Test
    void deactivateClient_retorna204() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent());
    }
}
