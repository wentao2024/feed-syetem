package com.feedsystem.post.service;

import com.feedsystem.common.exception.BusinessException;
import com.feedsystem.post.entity.Post;
import com.feedsystem.post.messaging.MessagePublisher;
import com.feedsystem.post.repository.LikeRepository;
import com.feedsystem.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock LikeRepository likeRepository;
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
        when(postRepository.save(any())).thenReturn(mockPost);

        var result = postService.createPost(1L, "alice", "Hello World!", null);

        assertThat(result.getContent()).isEqualTo("Hello World!");
        verify(messagePublisher).publishPostCreated(any());
    }

    @Test
    @DisplayName("likePost: increments like count")
    void likePost_success() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(likeRepository.existsByUserIdAndPostId(2L, 1L)).thenReturn(false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(mockPost));

        postService.likePost(1L, 2L);

        verify(likeRepository).save(any());
        assertThat(mockPost.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("likePost: throws when already liked")
    void likePost_alreadyLiked() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(likeRepository.existsByUserIdAndPostId(2L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> postService.likePost(1L, 2L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("deletePost: throws FORBIDDEN when not the author")
    void deletePost_forbidden() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(mockPost));

        assertThatThrownBy(() -> postService.deletePost(1L, 99L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Forbidden");
    }

    @Test
    @DisplayName("getPostsByIds: preserves requested order")
    void getPostsByIds_preservesOrder() {
        Post p2 = Post.builder().id(2L).userId(2L).content("Post 2").imageUrls(List.of()).likeCount(0).build();
        when(postRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(mockPost, p2));

        var result = postService.getPostsByIds(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }
}
