package com.feedsystem.feed.service;

import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.common.dto.RecentPostsRequest;
import com.feedsystem.feed.client.PostServiceClient;
import com.feedsystem.feed.client.UserServiceClient;
import com.feedsystem.feed.dto.FeedPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeedService {

    private static final String FEED_KEY_PREFIX = "feed:";
    private static final String POST_CACHE_KEY_PREFIX = "post:";
    private static final String LARGE_V_FOLLOWEES_KEY_PREFIX = "largev_followees:";
    private static final Duration POST_CACHE_TTL = Duration.ofHours(24);
    private static final Duration LARGE_V_FOLLOWEES_TTL = Duration.ofMinutes(5);
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
     * Cursor-based feed retrieval — hybrid push + pull.
     *
     * Push path: reads post IDs from the user's Redis ZSet (populated by fanOut for normal accounts).
     * Pull path: fetches recent posts directly from post-service for large-V followees (≥1000
     *            followers), whose fan-out was intentionally skipped at write time.
     * The two streams are merged by timestamp score and returned as a unified, sorted page.
     */
    public FeedPageResponse getFeed(Long userId, Long cursor, int size) {
        double maxScore = (cursor == null) ? Double.MAX_VALUE : (double) cursor - 1;

        // ── 1. Push-based feed from Redis ZSet ───────────────────────────
        String key = FEED_KEY_PREFIX + userId;
        Set<ZSetOperations.TypedTuple<String>> zsetItems = redisTemplate.opsForZSet()
            .reverseRangeByScoreWithScores(key, 0, maxScore, 0, size + 1);
        List<ZSetOperations.TypedTuple<String>> pushItems = (zsetItems != null)
            ? new ArrayList<>(zsetItems) : List.of();

        // ── 2. Large-V pull feed ──────────────────────────────────────────
        // large-V followee 列表用 Redis 缓存 5 分钟，避免每次请求都查 DB。
        List<Long> largeVIds = getLargeVFolloweeIdsCached(userId);
        Map<Long, PostDTO> largeVMap = new HashMap<>();
        if (!largeVIds.isEmpty()) {
            // 取 size+1 条，方便后续 hasMore 判断
            List<PostDTO> pulled = postServiceClient.getRecentPostsByAuthors(
                new RecentPostsRequest(largeVIds, cursor, size + 1));
            for (PostDTO dto : pulled) largeVMap.put(dto.getId(), dto);
        }

        // ── 3. Merge into unified (postId → score) map, sort by score desc ─
        Map<Long, Double> mergedScores = new HashMap<>();
        for (ZSetOperations.TypedTuple<String> t : pushItems) {
            mergedScores.put(Long.parseLong(t.getValue()), t.getScore());
        }

        for (PostDTO dto : largeVMap.values()) {
            double score = (double) dto.getCreatedAt()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            mergedScores.put(dto.getId(), score);
        }

        if (mergedScores.isEmpty()) {
            return FeedPageResponse.builder().posts(List.of()).hasMore(false).build();
        }

        List<Map.Entry<Long, Double>> sorted = mergedScores.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());

        boolean hasMore = sorted.size() > size;
        List<Map.Entry<Long, Double>> page = sorted.stream().limit(size).collect(Collectors.toList());

        // ── 4. Resolve PostDTOs for push items via Redis post cache ──────────
        // Large-V items already have full PostDTOs from step 2.
        List<Long> pushIdsToResolve = page.stream()
            .map(Map.Entry::getKey)
            .filter(id -> !largeVMap.containsKey(id))
            .collect(Collectors.toList());

        Map<Long, PostDTO> resolvedMap = new HashMap<>(largeVMap);
        if (!pushIdsToResolve.isEmpty()) {
            List<String> cacheKeys = pushIdsToResolve.stream()
                .map(id -> POST_CACHE_KEY_PREFIX + id)
                .collect(Collectors.toList());
            List<PostDTO> cached = postCacheTemplate.opsForValue().multiGet(cacheKeys);

            List<Long> missIds = new ArrayList<>();
            for (int i = 0; i < pushIdsToResolve.size(); i++) {
                if (cached != null && cached.get(i) != null) {
                    resolvedMap.put(pushIdsToResolve.get(i), cached.get(i));
                } else {
                    missIds.add(pushIdsToResolve.get(i));
                }
            }
            if (!missIds.isEmpty()) {
                log.debug("Post cache miss for {} ids, calling post-service", missIds.size());
                List<PostDTO> fetched = postServiceClient.getPostsByIds(missIds);
                for (PostDTO dto : fetched) {
                    resolvedMap.put(dto.getId(), dto);
                    postCacheTemplate.opsForValue().set(
                        POST_CACHE_KEY_PREFIX + dto.getId(), dto, POST_CACHE_TTL);
                }
            }
        }

        // ── 5. Build final list in merged sort order ─────────────────────────
        List<PostDTO> posts = page.stream()
            .map(e -> resolvedMap.get(e.getKey()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Long nextCursor = hasMore
            ? page.stream().mapToLong(e -> e.getValue().longValue()).min().orElse(0)
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

        // Pre-warm post cache asynchronously — runs in parallel with ZSet writes below.
        // Best-effort: failure only means a cache miss on first read, not a fanOut failure.
        CompletableFuture.runAsync(() -> {
            try {
                List<PostDTO> dtos = postServiceClient.getPostsByIds(List.of(postId));
                if (!dtos.isEmpty()) {
                    postCacheTemplate.opsForValue().set(POST_CACHE_KEY_PREFIX + postId, dtos.get(0), POST_CACHE_TTL);
                }
            } catch (Exception e) {
                log.warn("Failed to pre-warm cache for postId={}: {}", postId, e.getMessage());
            }
        });

        if (followerIds.size() >= LARGE_ACCOUNT_THRESHOLD) {
            log.info("Skipping push fan-out for large account authorId={}, followers={}", authorId, followerIds.size());
            return;
        }

        // Pipeline: collapse N*2 round trips into 1
        String postIdStr = String.valueOf(postId);
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations ops) {
                for (Long followerId : followerIds) {
                    String key = FEED_KEY_PREFIX + followerId;
                    ops.opsForZSet().add(key, postIdStr, scoreMillis);
                    ops.opsForZSet().removeRange(key, 0, -(MAX_FEED_SIZE + 1));
                }
                return null;
            }
        });
        log.info("Fan-out complete: postId={} pushed to {} followers", postId, followerIds.size());
    }

    /**
     * 关注时回填：从 post-service 查询 followee 自己发的最近 20 条帖子，
     * 写入 follower 的 feed ZSet。
     * 原来的实现读的是 feed:{followeeId}（followee 看到的个性化 Feed），
     * 会把 followee 关注的人的帖子也复制过来，逻辑错误。
     */
    public void backfillOnFollow(Long followerId, Long followeeId) {
        List<PostDTO> recentPosts = postServiceClient.getRecentPostsByAuthors(
            new RecentPostsRequest(List.of(followeeId), null, 20));

        if (recentPosts.isEmpty()) return;

        String followerFeedKey = FEED_KEY_PREFIX + followerId;
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations ops) {
                for (PostDTO post : recentPosts) {
                    long score = post.getCreatedAt()
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    ops.opsForZSet().add(followerFeedKey, String.valueOf(post.getId()), (double) score);
                }
                return null;
            }
        });

        log.info("Backfilled {} posts for followerId={} from followeeId={}", recentPosts.size(), followerId, followeeId);
    }

    /**
     * 从 Redis 缓存读取用户关注的大 V 列表，缓存 5 分钟。
     * 避免每次 getFeed 都触发 DB GROUP BY 聚合查询。
     */
    private List<Long> getLargeVFolloweeIdsCached(Long userId) {
        String key = LARGE_V_FOLLOWEES_KEY_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached.isEmpty()) return List.of();
            return java.util.Arrays.stream(cached.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        }
        List<Long> ids = userServiceClient.getLargeVFolloweeIds(userId);
        String value = ids.isEmpty() ? "" :
            ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        redisTemplate.opsForValue().set(key, value, LARGE_V_FOLLOWEES_TTL);
        return ids;
    }
}
