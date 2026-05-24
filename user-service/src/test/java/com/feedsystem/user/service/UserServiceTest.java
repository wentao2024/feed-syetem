package com.feedsystem.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feedsystem.common.exception.BusinessException;
import com.feedsystem.user.dto.LoginRequest;
import com.feedsystem.user.dto.RegisterRequest;
import com.feedsystem.user.entity.Follow;
import com.feedsystem.user.entity.User;
import com.feedsystem.user.mapper.FollowMapper;
import com.feedsystem.user.mapper.UserMapper;
import com.feedsystem.user.messaging.MessagePublisher;
import com.feedsystem.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock FollowMapper followMapper;
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
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");
        when(userMapper.insert(any(User.class))).thenReturn(1);
        when(jwtTokenProvider.generateToken(any(), anyString())).thenReturn("mock.jwt.token");

        var req = new RegisterRequest();
        req.setUsername("alice"); req.setEmail("alice@example.com"); req.setPassword("password123");

        var response = userService.register(req);

        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("register: throws when username taken")
    void register_duplicateUsername() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        var req = new RegisterRequest();
        req.setUsername("alice"); req.setEmail("alice@example.com"); req.setPassword("password123");

        assertThatThrownBy(() -> userService.register(req))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("login: success with correct credentials")
    void login_success() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);
        when(passwordEncoder.matches("password123", "$2a$hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, "alice")).thenReturn("mock.jwt.token");
        // countFollowers then countFollowing
        when(followMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L, 5L);

        var req = new LoginRequest();
        req.setUsername("alice"); req.setPassword("password123");

        var response = userService.login(req);
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUser().getFollowerCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("login: throws when password wrong")
    void login_wrongPassword() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        var req = new LoginRequest();
        req.setUsername("alice"); req.setPassword("wrong");

        assertThatThrownBy(() -> userService.login(req))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("follow: success and publishes event")
    void follow_success() {
        when(userMapper.selectById(2L)).thenReturn(User.builder().id(2L).username("bob").build());
        when(followMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        userService.follow(1L, 2L);

        verify(followMapper).insert(any(Follow.class));
        verify(messagePublisher).publishFollowEvent(any());
    }

    @Test
    @DisplayName("follow: throws when already following")
    void follow_alreadyFollowing() {
        when(userMapper.selectById(2L)).thenReturn(User.builder().id(2L).username("bob").build());
        when(followMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> userService.follow(1L, 2L))
            .isInstanceOf(BusinessException.class);
    }
}
