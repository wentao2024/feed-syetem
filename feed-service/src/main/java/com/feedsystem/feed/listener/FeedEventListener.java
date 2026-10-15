package com.feedsystem.feed.listener;

import com.feedsystem.common.event.FollowEvent;
import com.feedsystem.common.event.PostCreatedEvent;
import com.feedsystem.feed.config.RabbitMQConfig;
import com.feedsystem.feed.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedEventListener {

    private final FeedService feedService;

    @RabbitListener(queues = RabbitMQConfig.POST_CREATED_QUEUE)
    public void onPostCreated(PostCreatedEvent event) {
        log.info("Received post-created: postId={}, authorId={}", event.getPostId(), event.getAuthorId());
        feedService.fanOut(event.getPostId(), event.getAuthorId(), event.getCreatedAtMillis());
    }

    @RabbitListener(queues = RabbitMQConfig.FOLLOW_QUEUE)
    public void onFollowEvent(FollowEvent event) {
        log.info("Received follow event: {} -> {}, action={}", event.getFollowerId(), event.getFolloweeId(), event.getAction());
        if ("FOLLOW".equals(event.getAction())) {
            feedService.backfillOnFollow(event.getFollowerId(), event.getFolloweeId());
        }
    }
}
