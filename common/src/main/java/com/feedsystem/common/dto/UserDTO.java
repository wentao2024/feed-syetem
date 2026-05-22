package com.feedsystem.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String avatarUrl;
    private String bio;
    private Long followerCount;
    private Long followingCount;
    private LocalDateTime createdAt;
}
