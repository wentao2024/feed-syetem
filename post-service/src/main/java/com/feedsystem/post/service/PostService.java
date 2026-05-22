package com.feedsystem.post.service;

import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.common.event.PostCreatedEvent;
import com.feedsystem.common.exception.BusinessException;
import com.feedsystem.common.exception.ErrorCode;
import com.feedsystem.post.entity.Like;
import com.feedsystem.post.entity.Post;
import com.feedsystem.post.messaging.MessagePublisher;
import com.feedsystem.post.repository.LikeRepository;
import com.feedsystem.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final FileStorageService fileStorageService;
    private final MessagePublisher messagePublisher;

    @Transactional
    public PostDTO createPost(Long userId, String username, String content, List<MultipartFile> images) {
        List<String> imageUrls = (images == null) ? List.of() :
            images.stream()
                .filter(f -> f != null && !f.isEmpty())
                .map(fileStorageService::upload)
                .collect(Collectors.toList());

        Post post = Post.builder()
            .userId(userId)
            .content(content)
            .imageUrls(imageUrls)
            .build();
        postRepository.save(post);

        messagePublisher.publishPostCreated(PostCreatedEvent.builder()
            .postId(post.getId())
            .authorId(userId)
            .createdAtMillis(System.currentTimeMillis())
            .build());

        return toDTO(post, username, null, false);
    }

    public PostDTO getPost(Long postId, Long currentUserId) {
        Post post = findPostById(postId);
        boolean liked = currentUserId != null &&
            likeRepository.existsByUserIdAndPostId(currentUserId, postId);
        return toDTO(post, null, null, liked);
    }

    public Page<PostDTO> getUserPosts(Long userId, Long currentUserId, Pageable pageable) {
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(p -> {
                boolean liked = currentUserId != null &&
                    likeRepository.existsByUserIdAndPostId(currentUserId, p.getId());
                return toDTO(p, null, null, liked);
            });
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = findPostById(postId);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.getImageUrls().forEach(fileStorageService::delete);
        postRepository.delete(post);
    }

    @Transactional
    public void likePost(Long postId, Long userId) {
        if (!postRepository.existsById(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (likeRepository.existsByUserIdAndPostId(userId, postId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        likeRepository.save(Like.builder().userId(userId).postId(postId).build());
        postRepository.findById(postId).ifPresent(p -> {
            p.setLikeCount(p.getLikeCount() + 1);
            postRepository.save(p);
        });
    }

    @Transactional
    public void unlikePost(Long postId, Long userId) {
        Like like = likeRepository.findByUserIdAndPostId(userId, postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        likeRepository.delete(like);
        postRepository.findById(postId).ifPresent(p -> {
            p.setLikeCount(Math.max(0, p.getLikeCount() - 1));
            postRepository.save(p);
        });
    }

    public List<PostDTO> getPostsByIds(List<Long> postIds) {
        Map<Long, Post> postMap = postRepository.findByIdIn(postIds)
            .stream().collect(Collectors.toMap(Post::getId, p -> p));
        return postIds.stream()
            .filter(postMap::containsKey)
            .map(id -> toDTO(postMap.get(id), null, null, false))
            .collect(Collectors.toList());
    }

    private Post findPostById(Long postId) {
        return postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private PostDTO toDTO(Post post, String username, String avatarUrl, boolean likedByMe) {
        return PostDTO.builder()
            .id(post.getId())
            .userId(post.getUserId())
            .username(username)
            .avatarUrl(avatarUrl)
            .content(post.getContent())
            .imageUrls(post.getImageUrls())
            .likeCount(post.getLikeCount())
            .likedByMe(likedByMe)
            .createdAt(post.getCreatedAt())
            .build();
    }
}
