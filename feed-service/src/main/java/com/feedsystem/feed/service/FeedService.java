package com.feedsystem.feed.service;

import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.feed.client.PostServiceClient;
import com.feedsystem.feed.client.UserServiceClient;
import com.feedsystem.feed.dto.FeedPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final String FEED_KEY_PREFIX = "feed:";
    private static final int MAX_FEED_SIZE = 500;
    private static final int LARGE_ACCOUNT_THRESHOLD = 1000;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserServiceClient userServiceClient;
    private final PostServiceClient postServiceClient;

    /**
     * Cursor-based feed retrieval. cursor = last seen score (timestamp millis).
     * Avoids offset-based pagination which degrades with large offsets.
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

        List<PostDTO> posts = postServiceClient.getPostsByIds(postIds);

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
     */
    public void fanOut(Long postId, Long authorId, long scoreMillis) {
        List<Long> followerIds = userServiceClient.getFollowerIds(authorId);

        if (followerIds.size() >= LARGE_ACCOUNT_THRESHOLD) {
            log.info("Skipping push fan-out for large account authorId={}, followers={}", authorId, followerIds.size());
            return;
        }

        String postIdStr = String.valueOf(postId);
        for (Long followerId : followerIds) {
            String key = FEED_KEY_PREFIX + followerId;
            redisTemplate.opsForZSet().add(key, postIdStr, scoreMillis);
            // Cap feed length to avoid unbounded memory growth
            redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_FEED_SIZE + 1));
        }
        log.info("Fan-out complete: postId={} pushed to {} followers", postId, followerIds.size());
    }

    /**
     * When a user follows someone, backfill their recent posts into the follower's feed.
     */
    public void backfillOnFollow(Long followerId, Long followeeId) {
        // Pull latest N posts from followee's own feed and merge into follower's feed
        String followeeFeedKey = FEED_KEY_PREFIX + followeeId;
        Set<ZSetOperations.TypedTuple<String>> recentPosts = redisTemplate.opsForZSet()
            .reverseRangeWithScores(followeeFeedKey, 0, 19); // last 20 posts

        if (recentPosts == null || recentPosts.isEmpty()) return;

        String followerFeedKey = FEED_KEY_PREFIX + followerId;
        recentPosts.forEach(item ->
            redisTemplate.opsForZSet().add(followerFeedKey, item.getValue(), item.getScore()));

        log.info("Backfilled {} posts for followerId={} from followeeId={}", recentPosts.size(), followerId, followeeId);
    }
}
