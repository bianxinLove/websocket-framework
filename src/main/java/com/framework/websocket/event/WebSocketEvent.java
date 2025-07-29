package com.framework.websocket.event;

import lombok.Data;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 优化的WebSocket事件对象
 * 支持对象池复用，减少GC压力
 * 
 * @author bianxin
 * @version 2.0.0
 */
@Data
public class WebSocketEvent<T> {

    // 对象池实现
    private static final ConcurrentLinkedQueue<WebSocketEvent<?>> EVENT_POOL = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger POOL_SIZE = new AtomicInteger(0);
    private static final int MAX_POOL_SIZE = 1000; // 最大池大小
    private static final AtomicInteger TOTAL_CREATED = new AtomicInteger(0);
    private static final AtomicInteger TOTAL_REUSED = new AtomicInteger(0);

    /**
     * 事件类型
     */
    private WebSocketEventType eventType;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 服务标识
     */
    private String service;

    /**
     * 客户端IP（懒加载，只在需要时设置）
     */
    private String clientIp;

    /**
     * 事件数据
     */
    private T data;

    /**
     * 事件发生时间戳（使用long减少内存占用）
     */
    private long eventTimestamp;

    /**
     * 扩展属性（按需使用，减少内存占用）
     */
    private Object properties;

    /**
     * 错误信息（仅在错误事件时使用）
     */
    private String errorMessage;

    /**
     * 异常堆栈（仅在错误事件时使用，使用弱引用）
     */
    private transient Throwable throwable; // transient避免序列化时的内存占用

    // 对象池管理标记
    private transient boolean inPool = false;

    public WebSocketEvent() {
        this.eventTimestamp = System.currentTimeMillis();
    }

    private WebSocketEvent(WebSocketEventType eventType, String sessionId, T data) {
        this();
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.data = data;
    }

    private WebSocketEvent(WebSocketEventType eventType, String sessionId, String userId, String service, T data) {
        this(eventType, sessionId, data);
        this.userId = userId;
        this.service = service;
    }

    /**
     * 从对象池获取事件对象
     */
    @SuppressWarnings("unchecked")
    private static <T> WebSocketEvent<T> obtain() {
        WebSocketEvent<T> event = (WebSocketEvent<T>) EVENT_POOL.poll();
        if (event != null) {
            POOL_SIZE.decrementAndGet();
            event.inPool = false;
            event.reset(); // 重置状态
            TOTAL_REUSED.incrementAndGet();
            return event;
        } else {
            TOTAL_CREATED.incrementAndGet();
            return new WebSocketEvent<>();
        }
    }

    /**
     * 回收事件对象到池中
     */
    public void recycle() {
        if (!inPool && POOL_SIZE.get() < MAX_POOL_SIZE) {
            this.inPool = true;
            this.reset();
            EVENT_POOL.offer(this);
            POOL_SIZE.incrementAndGet();
        }
    }

    /**
     * 重置对象状态（复用时清理）
     */
    private void reset() {
        this.eventType = null;
        this.sessionId = null;
        this.userId = null;
        this.service = null;
        this.clientIp = null;
        this.data = null;
        this.eventTimestamp = System.currentTimeMillis();
        this.properties = null;
        this.errorMessage = null;
        this.throwable = null;
    }

    /**
     * 创建连接开启事件
     */
    public static <T> WebSocketEvent<T> onOpen(String sessionId, String userId, String service, T data) {
        WebSocketEvent<T> event = obtain();
        event.eventType = WebSocketEventType.ON_OPEN;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = data;
        return event;
    }

    /**
     * 创建连接关闭事件
     */
    public static <T> WebSocketEvent<T> onClose(String sessionId, String userId, String service, T data) {
        WebSocketEvent<T> event = obtain();
        event.eventType = WebSocketEventType.ON_CLOSE;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = data;
        return event;
    }

    /**
     * 创建消息接收事件
     */
    public static <T> WebSocketEvent<T> onMessage(String sessionId, String userId, String service, T data) {
        WebSocketEvent<T> event = obtain();
        event.eventType = WebSocketEventType.ON_MESSAGE;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = data;
        return event;
    }

    /**
     * 创建消息发送事件
     */
    public static <T> WebSocketEvent<T> onSend(String sessionId, String userId, String service, T data) {
        WebSocketEvent<T> event = obtain();
        event.eventType = WebSocketEventType.ON_SEND;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = data;
        return event;
    }

    /**
     * 创建错误事件
     */
    public static WebSocketEvent<String> onError(String sessionId, String userId, String service, String errorMessage, Throwable throwable) {
        WebSocketEvent<String> event = obtain();
        event.eventType = WebSocketEventType.ON_ERROR;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = errorMessage;
        event.errorMessage = errorMessage;
        event.throwable = throwable;
        return event;
    }

    /**
     * 创建心跳事件
     */
    public static <T> WebSocketEvent<T> onHeartbeat(String sessionId, String userId, String service, T data) {
        WebSocketEvent<T> event = obtain();
        event.eventType = WebSocketEventType.ON_HEARTBEAT;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = data;
        return event;
    }

    /**
     * 创建心跳超时事件
     */
    public static <T> WebSocketEvent<T> onHeartbeatTimeout(String sessionId, String userId, String service, T data) {
        WebSocketEvent<T> event = obtain();
        event.eventType = WebSocketEventType.ON_HEARTBEAT_TIMEOUT;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = data;
        return event;
    }

    /**
     * 创建自定义事件
     */
    public static <T> WebSocketEvent<T> custom(String sessionId, String userId, String service, T data) {
        WebSocketEvent<T> event = obtain();
        event.eventType = WebSocketEventType.CUSTOM;
        event.sessionId = sessionId;
        event.userId = userId;
        event.service = service;
        event.data = data;
        return event;
    }

    /**
     * 获取池统计信息
     */
    public static String getPoolStats() {
        return String.format("EventPool{当前池大小: %d, 总创建: %d, 总复用: %d, 复用率: %.1f%%}",
                POOL_SIZE.get(),
                TOTAL_CREATED.get(),
                TOTAL_REUSED.get(),
                TOTAL_CREATED.get() > 0 ? (double) TOTAL_REUSED.get() / (TOTAL_CREATED.get() + TOTAL_REUSED.get()) * 100 : 0);
    }

    /**
     * 清理对象池（在内存压力大时调用）
     */
    public static void clearPool() {
        EVENT_POOL.clear();
        POOL_SIZE.set(0);
    }

    /**
     * 获取事件时间（转换为易读格式）
     */
    public java.time.LocalDateTime getEventTime() {
        return java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(eventTimestamp),
                java.time.ZoneId.systemDefault()
        );
    }

    /**
     * 设置事件时间
     */
    public void setEventTime(java.time.LocalDateTime eventTime) {
        this.eventTimestamp = eventTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public String toString() {
        return String.format("WebSocketEvent{type=%s, session=%s, user=%s, service=%s, time=%d}",
                eventType, sessionId, userId, service, eventTimestamp);
    }
}