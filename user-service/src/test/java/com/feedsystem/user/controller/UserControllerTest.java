package com.feedsystem.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feedsystem.common.dto.UserDTO;
import com.feedsystem.user.config.SecurityConfig;
import com.feedsystem.user.dto.AuthResponse;
import com.feedsystem.user.dto.LoginRequest;
import com.feedsystem.user.dto.RegisterRequest;
import com.feedsystem.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  UserService userService;

    @Test
    @DisplayName("POST /api/users/register returns 200 with token")
    void register_returns200() throws Exception {
        var dto = UserDTO.builder().id(1L).username("alice").build();
        when(userService.register(any())).thenReturn(
            AuthResponse.builder().token("mock.token").user(dto).build());

        var request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").value("mock.token"))
            .andExpect(jsonPath("$.data.user.username").value("alice"));
    }

    @Test
    @DisplayName("POST /api/users/register returns 400 when username blank")
    void register_validation_fails() throws Exception {
        var request = new RegisterRequest();
        request.setUsername("");       // blank → fails @NotBlank
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users/login returns 200 with token")
    void login_returns200() throws Exception {
        var dto = UserDTO.builder().id(1L).username("alice").build();
        when(userService.login(any())).thenReturn(
            AuthResponse.builder().token("jwt.token.here").user(dto).build());

        var request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").value("jwt.token.here"));
    }
}
