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
         * 队列监控间隔（秒）
         */
        private int monitorInterval = 30;
        
        /**
         * 队列警告阈值
         */
        private int queueWarningThreshold = 1000;
        
        /**
         * 队列危险阈值
         */
        private int queueDangerThreshold = 5000;
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