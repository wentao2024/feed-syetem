package com.feedsystem.post.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE           = "feed.exchange";
    public static final String POST_CREATED_QUEUE = "post.created.queue";
    public static final String POST_CREATED_KEY   = "post.created";

    @Bean
    public TopicExchange feedExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue postCreatedQueue() {
        return QueueBuilder.durable(POST_CREATED_QUEUE).build();
    }

    @Bean
    public Binding postCreatedBinding(Queue postCreatedQueue, TopicExchange feedExchange) {
        return BindingBuilder.bind(postCreatedQueue).to(feedExchange).with(POST_CREATED_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
