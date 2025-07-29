package com.framework.websocket.config;

import com.framework.websocket.util.TimeoutTaskWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;

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
        
        // 启动队列容量监控
        startQueueMonitoring(executor);
        
        return executor;
    }
    
    /**
     * 任务超时包装器Bean
     */
    @Bean("timeoutTaskWrapper")
    public TimeoutTaskWrapper timeoutTaskWrapper(@Qualifier("webSocketExecutorService") ScheduledExecutorService executor) {
        WebSocketFrameworkProperties.ThreadPool threadPoolConfig = properties.getThreadPool();
        return new TimeoutTaskWrapper(executor, threadPoolConfig.getTaskTimeout());
    }

    /**
     * 启动队列容量监控
     * 防止任务队列无界增长导致OOM
     */
    private void startQueueMonitoring(ScheduledThreadPoolExecutor executor) {
        WebSocketFrameworkProperties.ThreadPool threadPoolConfig = properties.getThreadPool();
        
        // 使用配置的监控间隔
        executor.scheduleWithFixedDelay(() -> {
            try {
                int queueSize = executor.getQueue().size();
                int activeCount = executor.getActiveCount();
                long completedTaskCount = executor.getCompletedTaskCount();
                
                // 队列大小超过警告阈值
                if (queueSize > threadPoolConfig.getQueueWarningThreshold()) {
                    log.warn("线程池队列大小过大: queueSize={}, activeCount={}, completedTaskCount={}", 
                        queueSize, activeCount, completedTaskCount);
                }
                
                // 队列大小超过危险阈值，开始清理
                if (queueSize > threadPoolConfig.getQueueDangerThreshold()) {
                    log.error("线程池队列大小达到危险阈值，开始清理任务: queueSize={}", queueSize);
                    cleanupStaleTask(executor);
                }
                
                // 记录监控信息（调试模式）
                if (log.isDebugEnabled()) {
                    log.debug("线程池状态监控: queueSize={}, activeCount={}, completedTaskCount={}", 
                        queueSize, activeCount, completedTaskCount);
                }
            } catch (Exception e) {
                log.error("队列监控异常", e);
            }
        }, threadPoolConfig.getMonitorInterval(), threadPoolConfig.getMonitorInterval(), TimeUnit.SECONDS);
    }
    
    /**
     * 清理过期任务
     * 当队列大小过大时，清理可能的僵尸任务
     */
    private void cleanupStaleTask(ScheduledThreadPoolExecutor executor) {
        try {
            int queueSizeBefore = executor.getQueue().size();
            
            // 1. 清理已取消但未移除的任务
            executor.purge();
            
            int queueSizeAfterPurge = executor.getQueue().size();
            
            // 2. 如果purge()效果不明显，执行强制清理
            if (queueSizeAfterPurge > properties.getThreadPool().getQueueDangerThreshold() * 0.8) {
                forceCleanupTasks(executor);
            }
            
            int queueSizeAfter = executor.getQueue().size();
            log.info("任务清理完成: 清理前={}, purge后={}, 最终={}", 
                queueSizeBefore, queueSizeAfterPurge, queueSizeAfter);
                
        } catch (Exception e) {
            log.error("清理过期任务失败", e);
        }
    }
    
    /**
     * 强制清理任务队列
     * 当常规清理无效时的最后手段
     */
    private void forceCleanupTasks(ScheduledThreadPoolExecutor executor) {
        try {
            // 获取队列中的任务
            BlockingQueue<Runnable> queue = executor.getQueue();
            int originalSize = queue.size();
            
            // 强制清理队列中等待时间过长的任务
            queue.removeIf(task -> {
                try {
                    // 检查是否是延迟任务
                    if (task instanceof java.util.concurrent.RunnableScheduledFuture) {
                        java.util.concurrent.RunnableScheduledFuture<?> scheduledTask = 
                            (java.util.concurrent.RunnableScheduledFuture<?>) task;
                        
                        // 清理延迟时间过长的任务（超过5分钟）
                        long delay = scheduledTask.getDelay(TimeUnit.MILLISECONDS);
                        if (delay < -300000) { // 延迟超过5分钟的任务
                            scheduledTask.cancel(false);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.debug("检查任务时异常，移除该任务", e);
                    return true;
                }
                return false;
            });
            
            int cleanedCount = originalSize - queue.size();
            if (cleanedCount > 0) {
                log.warn("强制清理了{}个过期任务，剩余队列大小: {}", cleanedCount, queue.size());
            }
            
            // 如果队列仍然过大，临时拒绝新任务
            if (queue.size() > properties.getThreadPool().getQueueDangerThreshold()) {
                log.error("队列大小仍然过大({}), 建议考虑重启应用或增加线程池容量", queue.size());
                
                // 设置临时的拒绝策略（拒绝非关键任务）
                setTemporaryRejectPolicy(executor);
            }
            
        } catch (Exception e) {
            log.error("强制清理任务失败", e);
        }
    }
    
    /**
     * 设置临时拒绝策略
     * 当队列过载时临时拒绝新任务
     */
    private void setTemporaryRejectPolicy(ScheduledThreadPoolExecutor executor) {
        // 保存原始拒绝策略
        RejectedExecutionHandler originalHandler = executor.getRejectedExecutionHandler();
        
        // 设置临时拒绝策略
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("队列过载，拒绝执行任务: queueSize={}, activeCount={}", 
                e.getQueue().size(), e.getActiveCount());
        });
        
        // 60秒后恢复原始策略
        executor.schedule(() -> {
            executor.setRejectedExecutionHandler(originalHandler);
            log.info("已恢复原始拒绝策略");
        }, 60, TimeUnit.SECONDS);
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