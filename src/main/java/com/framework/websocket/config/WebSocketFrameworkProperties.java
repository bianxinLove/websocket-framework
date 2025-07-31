package com.framework.websocket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket框架配置属性
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "websocket.framework")
public class WebSocketFrameworkProperties {

    /**
     * 心跳配置
     */
    private Heartbeat heartbeat = new Heartbeat();
    
    /**
     * 线程池配置
     */
    private ThreadPool threadPool = new ThreadPool();
    
    /**
     * 会话配置
     */
    private Session session = new Session();
    
    /**
     * 消息配置
     */
    private Message message = new Message();
    
    /**
     * 功能开关配置
     */
    private Features features = new Features();

    @Data
    public static class Heartbeat {
        /**
         * 心跳间隔（秒）
         */
        private int interval = 30;
        
        /**
         * 心跳超时（秒）
         */
        private int timeout = 60;
    }

    @Data
    public static class ThreadPool {
        /**
         * 核心线程数
         */
        private int coreSize = 10;
        
        /**
         * 最大线程数
         */
        private int maxSize = 50;
        
        /**
         * 队列容量
         */
        private int queueCapacity = 1000;
        
        /**
         * 线程保活时间（秒）
         */
        private int keepAlive = 60;
        
        /**
         * 任务执行超时时间（秒）
         */
        private int taskTimeout = 300;
        
        /**
         * 队列警告阈值
         */
        private int queueWarningThreshold = 1000;
        
        /**
         * 队列危险阈值
         */
        private int queueDangerThreshold = 5000;
        
        /**
         * 监控配置
         */
        private Monitoring monitoring = new Monitoring();
    }
    
    @Data
    public static class Monitoring {
        /**
         * 启用智能监控
         */
        private boolean enabled = true;
        
        /**
         * 初始监控间隔（秒）
         */
        private int initialInterval = 30;
        
        /**
         * 最小监控间隔（秒）
         */
        private int minInterval = 5;
        
        /**
         * 最大监控间隔（秒）
         */
        private int maxInterval = 120;
        
        /**
         * 初始采样率（1表示每次都采样）
         */
        private int initialSamplingRate = 2;
        
        /**
         * 健康检查阈值配置
         */
        private HealthThresholds healthThresholds = new HealthThresholds();
    }
    
    @Data
    public static class HealthThresholds {
        /**
         * 线程池利用率警告阈值（0.0-1.0）
         */
        private double poolUtilizationWarning = 0.7;
        
        /**
         * 线程池利用率严重阈值（0.0-1.0）
         */
        private double poolUtilizationCritical = 0.9;
        
        /**
         * 队列利用率警告阈值（0.0-1.0）
         */
        private double queueUtilizationWarning = 0.5;
        
        /**
         * 队列利用率严重阈值（0.0-1.0）
         */
        private double queueUtilizationCritical = 0.8;
        
        /**
         * 任务拒绝率警告阈值（0.0-1.0）
         */
        private double rejectionRateWarning = 0.01;
        
        /**
         * 任务拒绝率严重阈值（0.0-1.0）
         */
        private double rejectionRateCritical = 0.05;
    }

    @Data
    public static class Session {
        /**
         * 最大空闲时间（秒）
         */
        private int maxIdleTime = 300;
        
        /**
         * 清理间隔（秒）
         */
        private int cleanupInterval = 60;
    }

    @Data
    public static class Message {
        /**
         * 最大消息大小（字节）
         */
        private long maxSize = 1048576;
        
        /**
         * 缓冲区大小
         */
        private int bufferSize = 8192;
    }

    @Data
    public static class Features {
        /**
         * 启用指标统计
         */
        private boolean metrics = true;
        
        /**
         * 启用健康检查
         */
        private boolean healthCheck = true;
        
        /**
         * 启用管理API
         */
        private boolean adminApi = true;
    }
}