package com.feedsystem.feed.service;

import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.feed.client.PostServiceClient;
import com.feedsystem.feed.client.UserServiceClient;
import com.feedsystem.feed.dto.FeedPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeedService {

    private static final String FEED_KEY_PREFIX = "feed:";
    private static final String POST_CACHE_KEY_PREFIX = "post:";
    private static final Duration POST_CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_FEED_SIZE = 500;
    private static final int LARGE_ACCOUNT_THRESHOLD = 1000;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, PostDTO> postCacheTemplate;
    private final UserServiceClient userServiceClient;
    private final PostServiceClient postServiceClient;

    public FeedService(
            RedisTemplate<String, String> redisTemplate,
            @Qualifier("postCacheTemplate") RedisTemplate<String, PostDTO> postCacheTemplate,
            UserServiceClient userServiceClient,
            PostServiceClient postServiceClient) {
        this.redisTemplate = redisTemplate;
        this.postCacheTemplate = postCacheTemplate;
        this.userServiceClient = userServiceClient;
        this.postServiceClient = postServiceClient;
    }

    /**
     * Cursor-based feed retrieval.
     * Reads post IDs from the user's Redis ZSet, resolves PostDTOs from a Redis cache
     * (post:{id}), and only calls post-service for cache misses — eliminating the Feign
     * bottleneck that caused 5s P99 under 150 concurrent readers.
     */
    public FeedPageResponse getFeed(Long userId, Long cursor, int size) {
        String key = FEED_KEY_PREFIX + userId;
        double maxScore = (cursor == null) ? Double.MAX_VALUE : (double) cursor - 1;

        Set<ZSetOperations.TypedTuple<String>> items = redisTemplate.opsForZSet()
            .reverseRangeByScoreWithScores(key, 0, maxScore, 0, size + 1);

        if (items == null || items.isEmpty()) {
            return FeedPageResponse.builder().posts(List.of()).hasMore(false).build();
        }

        boolean hasMore = items.size() > size;
        List<ZSetOperations.TypedTuple<String>> page = items.stream()
            .limit(size)
            .collect(Collectors.toList());

        List<Long> postIds = page.stream()
            .map(t -> Long.parseLong(t.getValue()))
            .collect(Collectors.toList());

        // 1. Batch-fetch from Redis post cache
        List<String> cacheKeys = postIds.stream()
            .map(id -> POST_CACHE_KEY_PREFIX + id)
            .collect(Collectors.toList());
        List<PostDTO> cached = postCacheTemplate.opsForValue().multiGet(cacheKeys);

        // 2. Find misses and call post-service only for those
        List<Long> missIds = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i++) {
            if (cached == null || cached.get(i) == null) {
                missIds.add(postIds.get(i));
            }
        }

        Map<Long, PostDTO> fetchedMap = new HashMap<>();
        if (!missIds.isEmpty()) {
            log.debug("Post cache miss for {} ids, calling post-service", missIds.size());
            List<PostDTO> fetched = postServiceClient.getPostsByIds(missIds);
            for (PostDTO dto : fetched) {
                fetchedMap.put(dto.getId(), dto);
                postCacheTemplate.opsForValue().set(POST_CACHE_KEY_PREFIX + dto.getId(), dto, POST_CACHE_TTL);
            }
        }

        // 3. Merge cached + freshly fetched in original ZSet order
        List<PostDTO> posts = new ArrayList<>(postIds.size());
        for (int i = 0; i < postIds.size(); i++) {
            PostDTO dto = (cached != null) ? cached.get(i) : null;
            if (dto == null) dto = fetchedMap.get(postIds.get(i));
            if (dto != null) posts.add(dto);
        }

        Long nextCursor = hasMore
            ? page.stream().mapToLong(t -> t.getScore().longValue()).min().orElse(0)
            : null;

        return FeedPageResponse.builder()
            .posts(posts)
            .nextCursor(nextCursor)
            .hasMore(hasMore)
            .size(posts.size())
            .build();
    }

    /**
     * Fan-out: push postId into each follower's Redis ZSET feed.
     * Skips large accounts (LARGE_ACCOUNT_THRESHOLD+) — their followers pull instead.
     * Also pre-warms the PostDTO cache so first reads after a new post are instant.
     */
    public void fanOut(Long postId, Long authorId, long scoreMillis) {
        List<Long> followerIds = userServiceClient.getFollowerIds(authorId);

        // Pre-warm post cache so getFeed() readers don't need to call post-service
        try {
            List<PostDTO> dtos = postServiceClient.getPostsByIds(List.of(postId));
            if (!dtos.isEmpty()) {
                postCacheTemplate.opsForValue().set(POST_CACHE_KEY_PREFIX + postId, dtos.get(0), POST_CACHE_TTL);
            }
        } catch (Exception e) {
            log.warn("Failed to pre-warm cache for postId={}: {}", postId, e.getMessage());
        }

        if (followerIds.size() >= LARGE_ACCOUNT_THRESHOLD) {
            log.info("Skipping push fan-out for large account authorId={}, followers={}", authorId, followerIds.size());
            return;
        }

        String postIdStr = String.valueOf(postId);
        for (Long followerId : followerIds) {
            String key = FEED_KEY_PREFIX + followerId;
            redisTemplate.opsForZSet().add(key, postIdStr, scoreMillis);
            redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_FEED_SIZE + 1));
        }
        log.info("Fan-out complete: postId={} pushed to {} followers", postId, followerIds.size());
    }

    /**
     * When a user follows someone, backfill their recent posts into the follower's feed.
     */
    public void backfillOnFollow(Long followerId, Long followeeId) {
        String followeeFeedKey = FEED_KEY_PREFIX + followeeId;
        Set<ZSetOperations.TypedTuple<String>> recentPosts = redisTemplate.opsForZSet()
            .reverseRangeWithScores(followeeFeedKey, 0, 19);

        if (recentPosts == null || recentPosts.isEmpty()) return;

        String followerFeedKey = FEED_KEY_PREFIX + followerId;
        recentPosts.forEach(item ->
            redisTemplate.opsForZSet().add(followerFeedKey, item.getValue(), item.getScore()));

        log.info("Backfilled {} posts for followerId={} from followeeId={}", recentPosts.size(), followerId, followeeId);
    }
}
