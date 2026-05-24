package com.feedsystem.feed.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.feedsystem.common.dto.PostDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Feed ZSet store: key="feed:{userId}", value="{postId}" (String), score=timestamp millis.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer ser = new StringRedisSerializer();
        template.setKeySerializer(ser);
        template.setValueSerializer(ser);
        template.setHashKeySerializer(ser);
        template.setHashValueSerializer(ser);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * PostDTO cache: key="post:{postId}", value=PostDTO (JSON), TTL managed by FeedService.
     * Eliminates Feign calls to post-service on cache-hit reads.
     */
    @Bean
    public RedisTemplate<String, PostDTO> postCacheTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, PostDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(mapper, PostDTO.class));

        template.afterPropertiesSet();
        return template;
    }
}
