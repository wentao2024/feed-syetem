package com.feedsystem.feed.client;

import com.feedsystem.common.dto.PostDTO;
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
}
