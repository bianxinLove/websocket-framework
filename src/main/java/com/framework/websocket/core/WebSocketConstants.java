package com.framework.websocket.core;

/**
 * WebSocket框架常量定义
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
public class WebSocketConstants {

    /**
     * 默认心跳间隔时间（秒）
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 30;

    /**
     * 默认心跳超时时间（秒）
     */
    public static final int DEFAULT_HEARTBEAT_TIMEOUT = 60;

    /**
     * 客户端IP属性键
     */
    public static final String CLIENT_IP_KEY = "clientIp";

    /**
     * 客户端User-Agent属性键
     */
    public static final String USER_AGENT_KEY = "userAgent";

    /**
     * 连接时间属性键
     */
    public static final String CONNECT_TIME_KEY = "connectTime";

    /**
     * WebSocket会话Redis缓存前缀
     */
    public static final String SESSION_CACHE_PREFIX = "websocket:session:";

    /**
     * WebSocket心跳Redis缓存前缀
     */
    public static final String HEARTBEAT_CACHE_PREFIX = "websocket:heartbeat:";

    /**
     * 默认的线程池名称
     */
    public static final String DEFAULT_THREAD_POOL_NAME = "WebSocketFramework";

    /**
     * 系统消息类型
     */
    public static final class MessageType {
        public static final String HEARTBEAT = "heartbeat";
        public static final String TEXT = "text";
        public static final String BINARY = "binary";
        public static final String SYSTEM = "system";
    }

    /**
     * WebSocket状态
     */
    public static final class Status {
        public static final String CONNECTING = "CONNECTING";
        public static final String CONNECTED = "CONNECTED";
        public static final String DISCONNECTING = "DISCONNECTING";
        public static final String DISCONNECTED = "DISCONNECTED";
        public static final String ERROR = "ERROR";
    }
}