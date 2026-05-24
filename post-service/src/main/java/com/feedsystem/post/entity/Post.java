package com.feedsystem.post.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "posts", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String content;

    @TableField(value = "image_urls", typeHandler = JacksonTypeHandler.class)
    @Builder.Default
    private List<String> imageUrls = List.of();

    @TableField("like_count")
    @Builder.Default
    private Integer likeCount = 0;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
