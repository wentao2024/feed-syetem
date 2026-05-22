package com.feedsystem.user.controller;

import com.feedsystem.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoints called by other microservices via OpenFeign.
 * Not exposed through the API Gateway to external clients.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{userId}/follower-ids")
    public List<Long> getFollowerIds(@PathVariable Long userId) {
        return userService.getFollowerIds(userId);
    }
}
