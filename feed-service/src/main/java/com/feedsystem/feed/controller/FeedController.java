package com.feedsystem.feed.controller;

import com.feedsystem.common.dto.ApiResponse;
import com.feedsystem.feed.dto.FeedPageResponse;
import com.feedsystem.feed.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Tag(name = "Feed API", description = "Personalized feed retrieval")
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    @Operation(summary = "Get personalized feed (cursor-based pagination)")
    public ResponseEntity<ApiResponse<FeedPageResponse>> getFeed(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "Cursor from previous response (null for first page)")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "Page size (max 50)")
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 50);
        return ResponseEntity.ok(ApiResponse.success(feedService.getFeed(userId, cursor, safeSize)));
    }
}
