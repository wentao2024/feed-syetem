package com.feedsystem.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDTO {

    private Long id;
    private Long userId;
    private String username;
    private String avatarUrl;
    private String content;
    private List<String> imageUrls;
    private Integer likeCount;
    private Boolean likedByMe;
    private LocalDateTime createdAt;

}
