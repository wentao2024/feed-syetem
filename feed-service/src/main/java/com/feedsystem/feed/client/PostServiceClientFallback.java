package com.feedsystem.feed.client;

import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.common.dto.RecentPostsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PostServiceClientFallback implements PostServiceClient {

    @Override
    public List<PostDTO> getPostsByIds(List<Long> postIds) {
        log.warn("PostService unavailable, returning empty posts for ids={}", postIds);
        return List.of();
    }

    @Override
    public List<PostDTO> getRecentPostsByAuthors(RecentPostsRequest request) {
        log.warn("PostService unavailable, skipping large-V pull for authors={}", request.getAuthorIds());
        return List.of();
    }
}
