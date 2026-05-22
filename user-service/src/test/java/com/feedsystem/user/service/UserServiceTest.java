package com.feedsystem.user.service;

import com.feedsystem.common.exception.BusinessException;
import com.feedsystem.user.dto.LoginRequest;
import com.feedsystem.user.dto.RegisterRequest;
import com.feedsystem.user.entity.User;
import com.feedsystem.user.messaging.MessagePublisher;
import com.feedsystem.user.repository.FollowRepository;
import com.feedsystem.user.repository.UserRepository;
import com.feedsystem.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock MessagePublisher messagePublisher;

    @InjectMocks UserService userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
            .id(1L).username("alice").email("alice@example.com").password("$2a$hashed")
            .build();
    }

    @Test
    @DisplayName("register: success when username and email are unique")
    void register_success() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");
        when(userRepository.save(any())).thenReturn(mockUser);
        when(jwtTokenProvider.generateToken(any(), anyString())).thenReturn("mock.jwt.token");

        var req = new RegisterRequest();
        req.setUsername("alice"); req.setEmail("alice@example.com"); req.setPassword("password123");

        var response = userService.register(req);

        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUser().getUsername()).isEqualTo("alice");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: throws when username taken")
    void register_duplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        var req = new RegisterRequest();
        req.setUsername("alice"); req.setEmail("alice@example.com"); req.setPassword("password123");

        assertThatThrownBy(() -> userService.register(req))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("login: success with correct credentials")
    void login_success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "$2a$hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, "alice")).thenReturn("mock.jwt.token");
        when(followRepository.countByFolloweeId(1L)).thenReturn(10L);
        when(followRepository.countByFollowerId(1L)).thenReturn(5L);

        var req = new LoginRequest();
        req.setUsername("alice"); req.setPassword("password123");

        var response = userService.login(req);
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUser().getFollowerCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("login: throws when password wrong")
    void login_wrongPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        var req = new LoginRequest();
        req.setUsername("alice"); req.setPassword("wrong");

        assertThatThrownBy(() -> userService.login(req))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("follow: success and publishes event")
    void follow_success() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(false);

        userService.follow(1L, 2L);

        verify(followRepository).save(any());
        verify(messagePublisher).publishFollowEvent(any());
    }

    @Test
    @DisplayName("follow: throws when already following")
    void follow_alreadyFollowing() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> userService.follow(1L, 2L))
            .isInstanceOf(BusinessException.class);
    }
}
