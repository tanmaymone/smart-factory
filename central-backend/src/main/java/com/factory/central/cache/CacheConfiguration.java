package com.factory.central.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = "mode", havingValue = "memory", matchIfMissing = true)
    LatestTagCache inMemoryLatestTagCache() {
        return new InMemoryLatestTagCache();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = "mode", havingValue = "redis")
    LettuceConnectionFactory lettuceConnectionFactory(CacheProperties properties) {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(properties.getRedisHost(), properties.getRedisPort());
        return new LettuceConnectionFactory(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = "mode", havingValue = "redis")
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        return new StringRedisTemplate(lettuceConnectionFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.cache", name = "mode", havingValue = "redis")
    LatestTagCache redisLatestTagCache(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        return new RedisLatestTagCache(stringRedisTemplate, objectMapper);
    }
}
