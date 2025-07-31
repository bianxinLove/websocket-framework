package com.framework.websocket.config;

import com.framework.websocket.util.TimeoutTaskWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket框架配置类
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Configuration
@EnableWebSocket
@EnableScheduling
@EnableConfigurationProperties(WebSocketFrameworkProperties.class)
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
     * 优化版本：移除内置监控，使用独立的智能监控器
     */
    @Bean("webSocketExecutorService")
    public ScheduledExecutorService webSocketExecutorService() {
        WebSocketFrameworkProperties.ThreadPool threadPoolConfig = properties.getThreadPool();
        
        // 使用优化的ScheduledThreadPoolExecutor
        OptimizedScheduledThreadPoolExecutor executor = new OptimizedScheduledThreadPoolExecutor(
            threadPoolConfig.getCoreSize(),
            new WebSocketThreadFactory("WebSocket-")
        );
        
        // 设置最大线程数
        executor.setMaximumPoolSize(threadPoolConfig.getMaxSize());
        
        // 设置线程保活时间
        executor.setKeepAliveTime(threadPoolConfig.getKeepAlive(), TimeUnit.SECONDS);
        
        // 允许核心线程超时（节省资源）
        executor.allowCoreThreadTimeOut(true);
        
        // 设置智能拒绝策略
        executor.setRejectedExecutionHandler(new SmartRejectedExecutionHandler());
        
        // 预热线程池
        executor.prestartAllCoreThreads();
        
        log.info("WebSocket线程池已创建: 核心线程={}, 最大线程={}, 队列容量={}", 
            threadPoolConfig.getCoreSize(), threadPoolConfig.getMaxSize(), threadPoolConfig.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * 任务超时包装器Bean（优化版本）
     */
    @Bean("timeoutTaskWrapper")
    public TimeoutTaskWrapper timeoutTaskWrapper(@Qualifier("webSocketExecutorService") ScheduledExecutorService executor) {
        WebSocketFrameworkProperties.ThreadPool threadPoolConfig = properties.getThreadPool();
        return new TimeoutTaskWrapper(executor, threadPoolConfig.getTaskTimeout());
    }

    /**
     * 优化的ScheduledThreadPoolExecutor
     * 去除了性能瓶颈的内置监控，改为外部监控
     */
    public static class OptimizedScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        
        private final AtomicLong totalTasks = new AtomicLong(0);
        private final AtomicLong rejectedTasks = new AtomicLong(0);
        
        public OptimizedScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
            super(corePoolSize, threadFactory);
        }
        
        @Override
        public void execute(Runnable command) {
            totalTasks.incrementAndGet();
            super.execute(command);
        }
        
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            totalTasks.incrementAndGet();
            return super.schedule(command, delay, unit);
        }
        
        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            totalTasks.incrementAndGet();
            return super.schedule(callable, delay, unit);
        }
        
        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            // 定期任务只计数一次，不重复计数
            totalTasks.incrementAndGet();
            return super.scheduleAtFixedRate(command, initialDelay, period, unit);
        }
        
        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            // 定期任务只计数一次，不重复计数
            totalTasks.incrementAndGet();
            return super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }
        
        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            
            // 只在异常情况下记录日志，避免性能开销
            if (t != null) {
                log.warn("任务执行异常", t);
            }
        }
        
        /**
         * 获取任务统计信息（供外部监控使用）
         */
        public TaskStatistics getTaskStatistics() {
            return new TaskStatistics(
                totalTasks.get(),
                rejectedTasks.get(), 
                getCompletedTaskCount(),
                getActiveCount(),
                getQueue().size()
            );
        }
        
        /**
         * 增加拒绝任务计数
         */
        public void incrementRejectedTasks() {
            rejectedTasks.incrementAndGet();
        }
    }
    
    /**
     * 智能拒绝执行处理器
     * 根据任务类型和系统状态采用不同的拒绝策略
     */
    private class SmartRejectedExecutionHandler implements RejectedExecutionHandler {
        
        private final AtomicLong lastWarningTime = new AtomicLong(0);
        private static final long WARNING_INTERVAL = 60000; // 1分钟警告间隔
        
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            // 安全的类型转换
            if (executor instanceof OptimizedScheduledThreadPoolExecutor) {
                ((OptimizedScheduledThreadPoolExecutor) executor).incrementRejectedTasks();
            }
            
            // 限制警告频率，避免日志洪水
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastWarningTime.get() > WARNING_INTERVAL) {
                lastWarningTime.set(currentTime);
                log.warn("线程池任务被拒绝: 活跃线程={}, 队列大小={}, 建议检查系统负载", 
                    executor.getActiveCount(), executor.getQueue().size());
            }
            
            // 尝试在调用线程中执行（降级策略）
            if (!executor.isShutdown()) {
                try {
                    r.run();
                    log.debug("任务在调用线程中执行完成");
                } catch (Exception e) {
                    log.error("调用线程执行任务失败", e);
                    // 如果调用线程执行也失败，则抛出异常
                    throw new RejectedExecutionException("任务执行被拒绝且调用线程执行失败", e);
                }
            } else {
                throw new RejectedExecutionException("线程池已关闭，任务被拒绝");
            }
        }
    }
    
    /**
     * 任务统计信息
     */
    public static class TaskStatistics {
        public final long totalTasks;
        public final long rejectedTasks;
        public final long completedTasks;
        public final int activeTasks;
        public final int queuedTasks;
        
        public TaskStatistics(long totalTasks, long rejectedTasks, long completedTasks, int activeTasks, int queuedTasks) {
            this.totalTasks = totalTasks;
            this.rejectedTasks = rejectedTasks;
            this.completedTasks = completedTasks;
            this.activeTasks = activeTasks;
            this.queuedTasks = queuedTasks;
        }
        
        public double getRejectionRate() {
            return totalTasks > 0 ? (double) rejectedTasks / totalTasks : 0.0;
        }
        
        public double getCompletionRate() {
            return totalTasks > 0 ? (double) completedTasks / totalTasks : 0.0;
        }
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