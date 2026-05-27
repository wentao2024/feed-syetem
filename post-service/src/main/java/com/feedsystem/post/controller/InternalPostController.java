package com.feedsystem.post.controller;

import com.feedsystem.common.dto.PostDTO;
import com.feedsystem.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal endpoints called by Feed Service via OpenFeign.
 */
@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
public class InternalPostController {

    private final PostService postService;

    @PostMapping("/batch")
    public List<PostDTO> getPostsByIds(@RequestBody List<Long> postIds) {

        return postService.getPostsByIds(postIds);
    }
}
