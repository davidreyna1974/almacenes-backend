package com.codigo2enter.almacenes.modules.auth.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.auth.dto.*;
import com.codigo2enter.almacenes.modules.auth.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;
    @MockBean JwtUtils jwtUtils;

    private UserResponseDTO buildResponse() {
        return UserResponseDTO.builder()
                .id(1L).username("tester").email("t@t.com")
                .active(true).createdAt(LocalDateTime.now())
                .roles(Set.of("ROLE_WAREHOUSEMAN")).build();
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @Test
    void login_bodyValido_retorna200ConToken() throws Exception {
        when(userService.login(any())).thenReturn(AuthResponseDTO.builder().token("jwt.tok").build());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.tok"));
    }

    @Test
    void login_sinUsername_retorna400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"p\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Gestión de usuarios (ADMIN) ───────────────────────────────────────

    @Test
    void getAllUsers_retorna200() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(buildResponse()));
        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("tester"));
    }

    @Test
    void createUser_bodyValido_retorna201() throws Exception {
        when(userService.createUser(any())).thenReturn(buildResponse());
        UserCreateDTO dto = UserCreateDTO.builder()
                .username("nuevo").password("Pass1234!").email("n@n.com")
                .roles(Set.of("ROLE_WAREHOUSEMAN")).build();

        mockMvc.perform(post("/api/v1/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    void createUser_sinRoles_retorna400() throws Exception {
        UserCreateDTO dto = UserCreateDTO.builder()
                .username("nuevo").password("Pass1234!").email("n@n.com")
                .roles(Set.of()).build();

        mockMvc.perform(post("/api/v1/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserById_retorna200() throws Exception {
        when(userService.getUserById(1L)).thenReturn(buildResponse());
        mockMvc.perform(get("/api/v1/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateUser_retorna200() throws Exception {
        when(userService.updateUser(eq(1L), any())).thenReturn(buildResponse());
        UserUpdateDTO dto = UserUpdateDTO.builder().username("tester").email("t@t.com").build();

        mockMvc.perform(put("/api/v1/auth/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void deactivateUser_retorna204() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void assignRoles_retorna200() throws Exception {
        when(userService.assignRoles(eq(1L), any())).thenReturn(buildResponse());
        UserRoleAssignDTO dto = UserRoleAssignDTO.builder()
                .roles(Set.of("ROLE_MANAGER")).build();

        mockMvc.perform(put("/api/v1/auth/users/1/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // ── Perfil propio ─────────────────────────────────────────────────────

    @Test
    void getMyProfile_retorna200() throws Exception {
        when(userService.getMyProfile()).thenReturn(buildResponse());
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_retorna204() throws Exception {
        ChangePasswordDTO dto = ChangePasswordDTO.builder()
                .currentPassword("Old123!").newPassword("New123!!").confirmPassword("New123!!").build();

        mockMvc.perform(put("/api/v1/auth/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_sinCurrentPassword_retorna400() throws Exception {
        ChangePasswordDTO dto = ChangePasswordDTO.builder()
                .newPassword("New123!!").confirmPassword("New123!!").build();

        mockMvc.perform(put("/api/v1/auth/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
