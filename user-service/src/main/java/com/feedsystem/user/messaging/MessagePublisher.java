package com.feedsystem.user.messaging;

import com.feedsystem.common.event.FollowEvent;
import com.feedsystem.user.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishFollowEvent(FollowEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.FOLLOW_KEY, event);
            log.info("Published follow event: {} -> {}, action={}",
                event.getFollowerId(), event.getFolloweeId(), event.getAction());
        } catch (Exception e) {
            log.error("Failed to publish follow event", e);
        }
    }
}
