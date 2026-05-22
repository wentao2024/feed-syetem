package com.feedsystem.user.service;

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
import com.feedsystem.user.messaging.MessagePublisher;
import com.feedsystem.user.repository.FollowRepository;
import com.feedsystem.user.repository.UserRepository;
import com.feedsystem.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MessagePublisher messagePublisher;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .build();
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return AuthResponse.builder()
            .token(token)
            .user(toDTO(user, 0L, 0L))
            .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        long followers = followRepository.countByFolloweeId(user.getId());
        long following = followRepository.countByFollowerId(user.getId());
        return AuthResponse.builder()
            .token(token)
            .user(toDTO(user, followers, following))
            .build();
    }

    public UserDTO getProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long followers = followRepository.countByFolloweeId(userId);
        long following = followRepository.countByFollowerId(userId);
        return toDTO(user, followers, following);
    }

    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getBio() != null)       user.setBio(request.getBio());

        userRepository.save(user);
        long followers = followRepository.countByFolloweeId(userId);
        long following = followRepository.countByFollowerId(userId);
        return toDTO(user, followers, following);
    }

    @Transactional
    public void follow(Long followerId, Long followeeId) {
        if (!userRepository.existsById(followeeId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
        }

        followRepository.save(Follow.builder()
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
        Follow follow = followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOLLOWING));

        followRepository.delete(follow);

        messagePublisher.publishFollowEvent(FollowEvent.builder()
            .followerId(followerId)
            .followeeId(followeeId)
            .action("UNFOLLOW")
            .build());
    }

    public Page<UserDTO> getFollowers(Long userId, Pageable pageable) {
        return followRepository.findByFolloweeId(userId, pageable)
            .map(f -> getProfile(f.getFollowerId()));
    }

    public Page<UserDTO> getFollowing(Long userId, Pageable pageable) {
        return followRepository.findByFollowerId(userId, pageable)
            .map(f -> getProfile(f.getFolloweeId()));
    }

    public List<Long> getFollowerIds(Long userId) {
        return followRepository.findFollowerIdsByFolloweeId(userId);
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
