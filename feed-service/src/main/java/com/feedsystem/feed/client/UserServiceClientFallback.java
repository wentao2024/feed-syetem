package com.feedsystem.feed.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public List<Long> getFollowerIds(Long userId) {
        log.warn("UserService unavailable, returning empty followers for userId={}", userId);
        return List.of();
    }

    @Override
    public List<Long> getLargeVFolloweeIds(Long userId) {
        log.warn("UserService unavailable, skipping large-V pull for userId={}", userId);
        return List.of();
    }
}
