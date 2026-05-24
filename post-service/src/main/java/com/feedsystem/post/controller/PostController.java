package com.feedsystem.post.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.feedsystem.common.dto.ApiResponse;
import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Post API", description = "Create, retrieve, and manage posts")
public class PostController {

    private final PostService postService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new post with optional images")
    public ResponseEntity<ApiResponse<PostDTO>> createPost(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Username") String username,
            @RequestParam("content") String content,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.ok(ApiResponse.success(
            postService.createPost(userId, username, content, images)));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get post by ID")
    public ResponseEntity<ApiResponse<PostDTO>> getPost(
            @PathVariable Long postId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(postService.getPost(postId, currentUserId)));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all posts by a user")
    public ResponseEntity<ApiResponse<IPage<PostDTO>>> getUserPosts(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
            postService.getUserPosts(userId, currentUserId, page, size)));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete a post")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long postId,
            @RequestHeader("X-User-Id") Long userId) {
        postService.deletePost(postId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{postId}/like")
    @Operation(summary = "Like a post")
    public ResponseEntity<ApiResponse<Void>> likePost(
            @PathVariable Long postId,
            @RequestHeader("X-User-Id") Long userId) {
        postService.likePost(postId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{postId}/like")
    @Operation(summary = "Unlike a post")
    public ResponseEntity<ApiResponse<Void>> unlikePost(
            @PathVariable Long postId,
            @RequestHeader("X-User-Id") Long userId) {
        postService.unlikePost(postId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
