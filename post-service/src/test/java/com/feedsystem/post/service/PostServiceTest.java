package com.feedsystem.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feedsystem.common.exception.BusinessException;
import com.feedsystem.post.entity.Like;
import com.feedsystem.post.entity.Post;
import com.feedsystem.post.mapper.LikeMapper;
import com.feedsystem.post.mapper.PostMapper;
import com.feedsystem.post.messaging.MessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostMapper postMapper;
    @Mock LikeMapper likeMapper;
    @Mock FileStorageService fileStorageService;
    @Mock MessagePublisher messagePublisher;

    @InjectMocks PostService postService;

    private Post mockPost;

    @BeforeEach
    void setUp() {
        mockPost = Post.builder()
            .id(1L).userId(1L).content("Hello World!").imageUrls(List.of()).likeCount(0)
            .build();
    }

    @Test
    @DisplayName("createPost: saves post and publishes RabbitMQ event")
    void createPost_success() {
        when(postMapper.insert(any(Post.class))).thenReturn(1);

        var result = postService.createPost(1L, "alice", "Hello World!", null);

        assertThat(result.getContent()).isEqualTo("Hello World!");
        verify(messagePublisher).publishPostCreated(any());
    }

    @Test
    @DisplayName("likePost: increments like count")
    void likePost_success() {
        when(postMapper.selectById(1L)).thenReturn(mockPost);
        when(likeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        postService.likePost(1L, 2L);

        verify(likeMapper).insert(any(Like.class));
        // 原子 SQL UPDATE，不再修改内存对象；验证 mapper 被正确调用
        verify(postMapper).incrementLikeCount(1L);
    }

    @Test
    @DisplayName("likePost: throws when already liked")
    void likePost_alreadyLiked() {
        when(postMapper.selectById(1L)).thenReturn(mockPost);
        when(likeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> postService.likePost(1L, 2L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deletePost: throws FORBIDDEN when not the author")
    void deletePost_forbidden() {
        when(postMapper.selectById(1L)).thenReturn(mockPost);

        assertThatThrownBy(() -> postService.deletePost(1L, 99L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Forbidden");
    }

    @Test
    @DisplayName("getPostsByIds: preserves requested order")
    void getPostsByIds_preservesOrder() {
        Post p2 = Post.builder().id(2L).userId(2L).content("Post 2").imageUrls(List.of()).likeCount(0).build();
        when(postMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(mockPost, p2));

        var result = postService.getPostsByIds(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }
}
