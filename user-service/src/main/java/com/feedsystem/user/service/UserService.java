package com.feedsystem.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedsystem.common.dto.UserDTO;
import com.feedsystem.common.event.FollowEvent;
import com.feedsystem.common.exception.BusinessException;
import com.feedsystem.common.exception.ErrorCode;
import com.feedsystem.user.dto.AuthResponse;
import com.feedsystem.user.dto.LoginRequest;
import com.feedsystem.user.dto.RegisterRequest;
import com.feedsystem.user.dto.UpdateProfileRequest;
import com.feedsystem.user.entity.Follow;
import com.feedsystem.user.entity.User;
import com.feedsystem.user.mapper.FollowMapper;
import com.feedsystem.user.mapper.UserMapper;
import com.feedsystem.user.messaging.MessagePublisher;
import com.feedsystem.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int LARGE_V_THRESHOLD = 1000;

    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MessagePublisher messagePublisher;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, request.getEmail())) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .build();
        userMapper.insert(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return AuthResponse.builder()
            .token(token)
            .user(toDTO(user, 0L, 0L))
            .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        long followers = countFollowers(user.getId());
        long following = countFollowing(user.getId());
        return AuthResponse.builder()
            .token(token)
            .user(toDTO(user, followers, following))
            .build();
    }

    public UserDTO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        return toDTO(user, countFollowers(userId), countFollowing(userId));
    }

    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getBio() != null)       user.setBio(request.getBio());
        userMapper.updateById(user);

        return toDTO(user, countFollowers(userId), countFollowing(userId));
    }

    @Transactional
    public void follow(Long followerId, Long followeeId) {
        if (userMapper.selectById(followeeId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId)) > 0) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
        }

        followMapper.insert(Follow.builder()
            .followerId(followerId)
            .followeeId(followeeId)
            .build());

        messagePublisher.publishFollowEvent(FollowEvent.builder()
            .followerId(followerId)
            .followeeId(followeeId)
            .action("FOLLOW")
            .build());
    }

    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        Follow follow = followMapper.selectOne(new LambdaQueryWrapper<Follow>()
            .eq(Follow::getFollowerId, followerId)
            .eq(Follow::getFolloweeId, followeeId));
        if (follow == null) throw new BusinessException(ErrorCode.NOT_FOLLOWING);

        followMapper.deleteById(follow.getId());

        messagePublisher.publishFollowEvent(FollowEvent.builder()
            .followerId(followerId)
            .followeeId(followeeId)
            .action("UNFOLLOW")
            .build());
    }

    public IPage<UserDTO> getFollowers(Long userId, int page, int size) {
        IPage<Follow> followPage = followMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId));
        return followPage.convert(f -> getProfile(f.getFollowerId()));
    }

    public IPage<UserDTO> getFollowing(Long userId, int page, int size) {
        IPage<Follow> followPage = followMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        return followPage.convert(f -> getProfile(f.getFolloweeId()));
    }

    public List<Long> getFollowerIds(Long userId) {
        return followMapper.selectFollowerIdsByFolloweeId(userId);
    }

    public List<Long> getLargeVFolloweeIds(Long userId) {
        return followMapper.selectLargeVFolloweeIds(userId, LARGE_V_THRESHOLD);
    }

    private long countFollowers(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
            .eq(Follow::getFolloweeId, userId));
    }

    private long countFollowing(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
            .eq(Follow::getFollowerId, userId));
    }

    private UserDTO toDTO(User user, long followers, long following) {
        return UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .avatarUrl(user.getAvatarUrl())
            .bio(user.getBio())
            .followerCount(followers)
            .followingCount(following)
            .createdAt(user.getCreatedAt())
            .build();
    }
}
