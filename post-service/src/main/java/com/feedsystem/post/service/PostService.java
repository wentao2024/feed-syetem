package com.feedsystem.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.common.event.PostCreatedEvent;
import com.feedsystem.common.exception.BusinessException;
import com.feedsystem.common.exception.ErrorCode;
import com.feedsystem.post.entity.Like;
import com.feedsystem.post.entity.Post;
import com.feedsystem.post.mapper.LikeMapper;
import com.feedsystem.post.mapper.PostMapper;
import com.feedsystem.post.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final LikeMapper likeMapper;
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
        postMapper.insert(post);

        messagePublisher.publishPostCreated(PostCreatedEvent.builder()
            .postId(post.getId())
            .authorId(userId)
            .createdAtMillis(System.currentTimeMillis())
            .build());

        return toDTO(post, username, null, false);
    }

    public PostDTO getPost(Long postId, Long currentUserId) {
        Post post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        boolean liked = currentUserId != null && likeMapper.selectCount(new LambdaQueryWrapper<Like>()
            .eq(Like::getUserId, currentUserId).eq(Like::getPostId, postId)) > 0;
        return toDTO(post, null, null, liked);
    }

    public IPage<PostDTO> getUserPosts(Long userId, Long currentUserId, int page, int size) {
        IPage<Post> postPage = postMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .orderByDesc(Post::getCreatedAt));
        return postPage.convert(p -> {
            boolean liked = currentUserId != null && likeMapper.selectCount(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, currentUserId).eq(Like::getPostId, p.getId())) > 0;
            return toDTO(p, null, null, liked);
        });
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        if (!post.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        post.getImageUrls().forEach(fileStorageService::delete);
        postMapper.deleteById(postId);
    }

    @Transactional
    public void likePost(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        if (likeMapper.selectCount(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId).eq(Like::getPostId, postId)) > 0) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        likeMapper.insert(Like.builder().userId(userId).postId(postId).build());
        post.setLikeCount(post.getLikeCount() + 1);
        postMapper.updateById(post);
    }

    @Transactional
    public void unlikePost(Long postId, Long userId) {
        Like like = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
            .eq(Like::getUserId, userId).eq(Like::getPostId, postId));
        if (like == null) throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        likeMapper.deleteById(like.getId());
        Post post = postMapper.selectById(postId);
        if (post != null) {
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postMapper.updateById(post);
        }
    }

    public List<PostDTO> getPostsByIds(List<Long> postIds) {
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds)
            .stream().collect(Collectors.toMap(Post::getId, p -> p));
        return postIds.stream()
            .filter(postMap::containsKey)
            .map(id -> toDTO(postMap.get(id), null, null, false))
            .collect(Collectors.toList());
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
