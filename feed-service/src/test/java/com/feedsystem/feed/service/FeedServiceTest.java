package com.feedsystem.feed.service;

import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.feed.client.PostServiceClient;
import com.feedsystem.feed.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ZSetOperations<String, String> zSetOps;
    @Mock ValueOperations<String, String> stringValueOps;
    @Mock RedisTemplate<String, PostDTO> postCacheTemplate;
    @Mock ValueOperations<String, PostDTO> valueOps;
    @Mock UserServiceClient userServiceClient;
    @Mock PostServiceClient postServiceClient;

    // Manually constructed to avoid Mockito ambiguity with two RedisTemplate mocks
    FeedService feedService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        feedService = new FeedService(redisTemplate, postCacheTemplate, userServiceClient, postServiceClient);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(stringValueOps);
        // 缓存默认 miss，走实际的 userServiceClient 调用
        lenient().when(stringValueOps.get(anyString())).thenReturn(null);
        lenient().when(postCacheTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(postServiceClient.getPostsByIds(any())).thenReturn(List.of());
        lenient().when(postServiceClient.getRecentPostsByAuthors(any())).thenReturn(List.of());
        lenient().when(userServiceClient.getLargeVFolloweeIds(any())).thenReturn(List.of());

        // executePipelined uses an internal proxy as `ops`, bypassing the mock.
        // Intercept it and re-invoke the callback with the mock itself so zSetOps is used.
        lenient().doAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            callback.execute(redisTemplate);
            return List.of();
        }).when(redisTemplate).executePipelined(any(SessionCallback.class));
    }

    // ── getFeed ──────────────────────────────────────────────

    @Test
    @DisplayName("getFeed: returns empty when Redis has no entries")
    void getFeed_emptyFeed() {
        when(zSetOps.reverseRangeByScoreWithScores(eq("feed:1"), anyDouble(), anyDouble(), anyLong(), anyLong()))
            .thenReturn(Set.of());

        var result = feedService.getFeed(1L, null, 20);

        assertThat(result.getPosts()).isEmpty();
        assertThat(result.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("getFeed: returns posts and sets hasMore=true when more exist")
    void getFeed_withPosts_hasMore() {
        // Simulate 21 items returned (size=20, so hasMore=true)
        Set<ZSetOperations.TypedTuple<String>> items = new TreeSet<>(
            (a, b) -> Double.compare(b.getScore(), a.getScore()));
        for (int i = 21; i >= 1; i--) {
            final int idx = i;
            items.add(new ZSetOperations.TypedTuple<String>() {
                public String getValue() { return String.valueOf(idx); }
                public Double getScore() { return (double)(1000L + idx); }
                public int compareTo(ZSetOperations.TypedTuple<String> o) {
                    return Double.compare(o.getScore(), this.getScore());
                }
            });
        }
        when(zSetOps.reverseRangeByScoreWithScores(eq("feed:1"), anyDouble(), anyDouble(), anyLong(), anyLong()))
            .thenReturn(items);

        // Simulate all cache misses so the Feign call is made
        List<PostDTO> nullList = new ArrayList<>();
        for (int i = 0; i < 20; i++) nullList.add(null);
        when(valueOps.multiGet(any())).thenReturn(nullList);

        List<PostDTO> mockPosts = List.of(PostDTO.builder().id(1L).content("Post 1").build());
        when(postServiceClient.getPostsByIds(any())).thenReturn(mockPosts);

        var result = feedService.getFeed(1L, null, 20);

        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextCursor()).isNotNull();
    }

    @Test
    @DisplayName("getFeed: serves posts from cache without calling post-service")
    void getFeed_cacheHit_noFeignCall() {
        Set<ZSetOperations.TypedTuple<String>> items = new TreeSet<>(
            (a, b) -> Double.compare(b.getScore(), a.getScore()));
        items.add(new ZSetOperations.TypedTuple<String>() {
            public String getValue() { return "42"; }
            public Double getScore() { return 2000.0; }
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(o.getScore(), this.getScore());
            }
        });
        when(zSetOps.reverseRangeByScoreWithScores(eq("feed:1"), anyDouble(), anyDouble(), anyLong(), anyLong()))
            .thenReturn(items);

        // Cache hit: multiGet returns the PostDTO directly
        PostDTO cached = PostDTO.builder().id(42L).content("cached post").build();
        when(valueOps.multiGet(any())).thenReturn(List.of(cached));

        var result = feedService.getFeed(1L, null, 20);

        assertThat(result.getPosts()).hasSize(1);
        assertThat(result.getPosts().get(0).getId()).isEqualTo(42L);
        // Feign must NOT be called when cache hit
        verify(postServiceClient, never()).getPostsByIds(any());
    }

    // ── fanOut ───────────────────────────────────────────────

    @Test
    @DisplayName("fanOut: writes to all followers when follower count < 1000")
    void fanOut_pushToFollowers() {
        when(userServiceClient.getFollowerIds(1L)).thenReturn(List.of(10L, 11L, 12L));

        feedService.fanOut(99L, 1L, System.currentTimeMillis());

        // Each follower gets a ZADD
        verify(zSetOps, times(3)).add(anyString(), eq("99"), anyDouble());
    }

    @Test
    @DisplayName("fanOut: skips push for large accounts (>=1000 followers)")
    void fanOut_skipsLargeAccount() {
        List<Long> bigFollowerList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) bigFollowerList.add((long) i);
        when(userServiceClient.getFollowerIds(1L)).thenReturn(bigFollowerList);

        feedService.fanOut(99L, 1L, System.currentTimeMillis());

        verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
    }

    // ── large-V read path ────────────────────────────────────

    @Test
    @DisplayName("getFeed: large-V posts are pulled from post-service and merged with ZSet feed")
    void getFeed_largeV_mergedWithPushFeed() {
        // ZSet has one push post (score=1000 — intentionally tiny so large-V post sorts first)
        Set<ZSetOperations.TypedTuple<String>> pushItems = new TreeSet<>(
            (a, b) -> Double.compare(b.getScore(), a.getScore()));
        pushItems.add(new ZSetOperations.TypedTuple<String>() {
            public String getValue() { return "10"; }
            public Double getScore() { return 1000.0; }
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(o.getScore(), this.getScore());
            }
        });
        when(zSetOps.reverseRangeByScoreWithScores(eq("feed:1"), anyDouble(), anyDouble(), anyLong(), anyLong()))
            .thenReturn(pushItems);

        // Push post resolved from cache
        PostDTO pushPost = PostDTO.builder().id(10L).content("push post")
            .createdAt(LocalDateTime.of(2024, 1, 1, 10, 0)).build();
        when(valueOps.multiGet(any())).thenReturn(List.of(pushPost));

        // Viewer follows one large-V
        when(userServiceClient.getLargeVFolloweeIds(1L)).thenReturn(List.of(99L));

        // Large-V has a post with a 2024 timestamp — epoch millis >> 1000, so it sorts first
        PostDTO largeVPost = PostDTO.builder().id(20L).content("large-V post")
            .createdAt(LocalDateTime.of(2024, 1, 1, 11, 0)).build();
        when(postServiceClient.getRecentPostsByAuthors(any())).thenReturn(List.of(largeVPost));

        var result = feedService.getFeed(1L, null, 20);

        assertThat(result.getPosts()).hasSize(2);
        // Large-V post has larger epoch millis → must come first after merge-sort
        assertThat(result.getPosts().get(0).getId()).isEqualTo(20L);
        assertThat(result.getPosts().get(1).getId()).isEqualTo(10L);
        verify(postServiceClient).getRecentPostsByAuthors(any());
        // Push post was cache-hit — getPostsByIds must NOT be called
        verify(postServiceClient, never()).getPostsByIds(any());
    }

    @Test
    @DisplayName("getFeed: getRecentPostsByAuthors never called when user has no large-V followees")
    void getFeed_noLargeVFollowees_skipsPullPath() {
        when(zSetOps.reverseRangeByScoreWithScores(eq("feed:1"), anyDouble(), anyDouble(), anyLong(), anyLong()))
            .thenReturn(Set.of());
        // getLargeVFolloweeIds is lenient-stubbed to return List.of() in @BeforeEach

        feedService.getFeed(1L, null, 20);

        verify(postServiceClient, never()).getRecentPostsByAuthors(any());
    }
}
