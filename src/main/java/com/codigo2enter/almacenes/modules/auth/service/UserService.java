package com.codigo2enter.almacenes.modules.auth.service;

import com.codigo2enter.almacenes.modules.auth.dto.UserRequestDTO;
import com.codigo2enter.almacenes.modules.auth.dto.UserResponseDTO;

public interface UserService {

    UserResponseDTO registerUser(UserRequestDTO request);
}
