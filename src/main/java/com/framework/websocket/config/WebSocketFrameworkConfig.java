package com.framework.websocket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket框架配置类
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Configuration
@EnableWebSocket
@EnableScheduling
@Slf4j
public class WebSocketFrameworkConfig {

    @Autowired
    private WebSocketFrameworkProperties properties;

    /**
     * 注册WebSocket端点
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * WebSocket专用线程池
     * 使用自定义ScheduledThreadPoolExecutor以支持完整配置
     */
    @Bean("webSocketExecutorService")
    public ScheduledExecutorService webSocketExecutorService() {
        WebSocketFrameworkProperties.ThreadPool threadPoolConfig = properties.getThreadPool();
        
        // 使用自定义ScheduledThreadPoolExecutor支持完整配置
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
            threadPoolConfig.getCoreSize(),
            new WebSocketThreadFactory("WebSocket-")
        );
        
        // 设置最大线程数（当核心线程忙碌时扩展）
        executor.setMaximumPoolSize(threadPoolConfig.getMaxSize());
        
        // 设置线程保活时间
        executor.setKeepAliveTime(threadPoolConfig.getKeepAlive(), TimeUnit.SECONDS);
        
        // 允许核心线程超时（节省资源）
        executor.allowCoreThreadTimeOut(true);
        
        // 设置拒绝策略（心跳检测失败时记录日志）
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("心跳检测任务被拒绝执行，线程池可能过载: activeCount={}, queueSize={}", 
                e.getActiveCount(), e.getQueue().size());
        });
        
        return executor;
    }

    /**
     * Redis模板配置
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 设置key序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 设置value序列化器
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 自定义线程工厂
     */
    private static class WebSocketThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        WebSocketThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}