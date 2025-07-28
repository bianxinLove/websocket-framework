package com.framework.websocket.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池健康检查器
 * 监控线程池状态，提供健康检查信息
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class ThreadPoolHealthChecker {
    
    @Autowired
    @Qualifier("webSocketExecutorService")
    private ScheduledExecutorService executorService;
    
    private volatile boolean isHealthy = true;
    private volatile String healthStatus = "OK";
    
    @PostConstruct
    public void init() {
        startHealthCheck();
    }
    
    @PreDestroy
    public void destroy() {
        log.info("线程池健康检查器正在关闭...");
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        if (executorService instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) executorService;
            
            // 每分钟检查一次健康状态
            executor.scheduleWithFixedDelay(() -> {
                try {
                    checkHealth(executor);
                } catch (Exception e) {
                    log.error("健康检查异常", e);
                    isHealthy = false;
                    healthStatus = "检查异常: " + e.getMessage();
                }
            }, 60, 60, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 检查线程池健康状态
     */
    private void checkHealth(ScheduledThreadPoolExecutor executor) {
        int activeCount = executor.getActiveCount();
        int corePoolSize = executor.getCorePoolSize();
        int maximumPoolSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();
        long completedTaskCount = executor.getCompletedTaskCount();
        
        // 判断健康状态
        boolean wasHealthy = isHealthy;
        StringBuilder statusBuilder = new StringBuilder();
        
        // 检查活跃线程比例
        double activeRatio = (double) activeCount / maximumPoolSize;
        if (activeRatio > 0.9) {
            isHealthy = false;
            statusBuilder.append("活跃线程比例过高(").append(String.format("%.1f%%", activeRatio * 100)).append("); ");
        }
        
        // 检查队列积压
        if (queueSize > 2000) {
            isHealthy = false;
            statusBuilder.append("队列积压严重(").append(queueSize).append("个任务); ");
        }
        
        // 检查线程池是否关闭
        if (executor.isShutdown()) {
            isHealthy = false;
            statusBuilder.append("线程池已关闭; ");
        }
        
        // 检查是否终止
        if (executor.isTerminated()) {
            isHealthy = false;
            statusBuilder.append("线程池已终止; ");
        }
        
        if (isHealthy) {
            healthStatus = "OK";
            if (!wasHealthy) {
                log.info("线程池状态已恢复正常");
            }
        } else {
            healthStatus = statusBuilder.toString();
            log.warn("线程池健康检查失败: {}", healthStatus);
        }
        
        // 记录详细统计信息（调试模式）
        if (log.isDebugEnabled()) {
            log.debug("线程池健康检查: healthy={}, activeCount={}, corePoolSize={}, maxPoolSize={}, queueSize={}, completedTaskCount={}", 
                isHealthy, activeCount, corePoolSize, maximumPoolSize, queueSize, completedTaskCount);
        }
    }
    
    /**
     * 获取健康状态
     */
    public boolean isHealthy() {
        return isHealthy;
    }
    
    /**
     * 获取健康状态描述
     */
    public String getHealthStatus() {
        return healthStatus;
    }
    
    /**
     * 获取详细健康信息
     */
    public ThreadPoolHealthInfo getHealthInfo() {
        if (executorService instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) executorService;
            
            return ThreadPoolHealthInfo.builder()
                .healthy(isHealthy)
                .status(healthStatus)
                .activeCount(executor.getActiveCount())
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .queueSize(executor.getQueue().size())
                .completedTaskCount(executor.getCompletedTaskCount())
                .isShutdown(executor.isShutdown())
                .isTerminated(executor.isTerminated())
                .build();
        }
        
        return ThreadPoolHealthInfo.builder()
            .healthy(false)
            .status("无法获取线程池信息")
            .build();
    }
    
    /**
     * 线程池健康信息
     */
    public static class ThreadPoolHealthInfo {
        private boolean healthy;
        private String status;
        private int activeCount;
        private int corePoolSize;
        private int maximumPoolSize;
        private int queueSize;
        private long completedTaskCount;
        private boolean isShutdown;
        private boolean isTerminated;
        
        public static ThreadPoolHealthInfoBuilder builder() {
            return new ThreadPoolHealthInfoBuilder();
        }
        
        // Getters
        public boolean isHealthy() { return healthy; }
        public String getStatus() { return status; }
        public int getActiveCount() { return activeCount; }
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int getQueueSize() { return queueSize; }
        public long getCompletedTaskCount() { return completedTaskCount; }
        public boolean isShutdown() { return isShutdown; }
        public boolean isTerminated() { return isTerminated; }
        
        public static class ThreadPoolHealthInfoBuilder {
            private ThreadPoolHealthInfo info = new ThreadPoolHealthInfo();
            
            public ThreadPoolHealthInfoBuilder healthy(boolean healthy) {
                info.healthy = healthy;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder status(String status) {
                info.status = status;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder activeCount(int activeCount) {
                info.activeCount = activeCount;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder corePoolSize(int corePoolSize) {
                info.corePoolSize = corePoolSize;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder maximumPoolSize(int maximumPoolSize) {
                info.maximumPoolSize = maximumPoolSize;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder queueSize(int queueSize) {
                info.queueSize = queueSize;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder completedTaskCount(long completedTaskCount) {
                info.completedTaskCount = completedTaskCount;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder isShutdown(boolean isShutdown) {
                info.isShutdown = isShutdown;
                return this;
            }
            
            public ThreadPoolHealthInfoBuilder isTerminated(boolean isTerminated) {
                info.isTerminated = isTerminated;
                return this;
            }
            
            public ThreadPoolHealthInfo build() {
                return info;
            }
        }
    }
}