package com.feedsystem.feed.client;

import com.feedsystem.common.dto.PostDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "post-service", fallback = PostServiceClientFallback.class)
public interface PostServiceClient {

    @PostMapping("/internal/posts/batch")
    List<PostDTO> getPostsByIds(@RequestBody List<Long> postIds);
}
