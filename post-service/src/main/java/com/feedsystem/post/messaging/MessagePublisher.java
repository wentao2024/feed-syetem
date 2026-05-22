package com.feedsystem.post.messaging;

import com.feedsystem.common.event.PostCreatedEvent;
import com.feedsystem.post.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishPostCreated(PostCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.POST_CREATED_KEY, event);
            log.info("Published post-created event: postId={}, authorId={}",
                event.getPostId(), event.getAuthorId());
        } catch (Exception e) {
            log.error("Failed to publish post-created event", e);
        }
    }
}
