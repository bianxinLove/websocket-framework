package com.framework.websocket.event;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket事件对象
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Data
public class WebSocketEvent<T> {

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
     * 客户端IP
     */
    private String clientIp;

    /**
     * 事件数据
     */
    private T data;

    /**
     * 事件发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 扩展属性
     */
    private Object properties;

    /**
     * 错误信息（仅在错误事件时使用）
     */
    private String errorMessage;

    /**
     * 异常堆栈（仅在错误事件时使用）
     */
    private Throwable throwable;

    public WebSocketEvent() {
        this.eventTime = LocalDateTime.now();
    }

    public WebSocketEvent(WebSocketEventType eventType, String sessionId, T data) {
        this();
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.data = data;
    }

    public WebSocketEvent(WebSocketEventType eventType, String sessionId, String userId, String service, T data) {
        this(eventType, sessionId, data);
        this.userId = userId;
        this.service = service;
    }

    /**
     * 创建连接开启事件
     */
    public static <T> WebSocketEvent<T> onOpen(String sessionId, String userId, String service, T data) {
        return new WebSocketEvent<>(WebSocketEventType.ON_OPEN, sessionId, userId, service, data);
    }

    /**
     * 创建连接关闭事件
     */
    public static <T> WebSocketEvent<T> onClose(String sessionId, String userId, String service, T data) {
        return new WebSocketEvent<>(WebSocketEventType.ON_CLOSE, sessionId, userId, service, data);
    }

    /**
     * 创建消息接收事件
     */
    public static <T> WebSocketEvent<T> onMessage(String sessionId, String userId, String service, T data) {
        return new WebSocketEvent<>(WebSocketEventType.ON_MESSAGE, sessionId, userId, service, data);
    }

    /**
     * 创建消息发送事件
     */
    public static <T> WebSocketEvent<T> onSend(String sessionId, String userId, String service, T data) {
        return new WebSocketEvent<>(WebSocketEventType.ON_SEND, sessionId, userId, service, data);
    }

    /**
     * 创建错误事件
     */
    public static WebSocketEvent<String> onError(String sessionId, String userId, String service, String errorMessage, Throwable throwable) {
        WebSocketEvent<String> event = new WebSocketEvent<>(WebSocketEventType.ON_ERROR, sessionId, userId, service, errorMessage);
        event.setErrorMessage(errorMessage);
        event.setThrowable(throwable);
        return event;
    }

    /**
     * 创建心跳事件
     */
    public static <T> WebSocketEvent<T> onHeartbeat(String sessionId, String userId, String service, T data) {
        return new WebSocketEvent<>(WebSocketEventType.ON_HEARTBEAT, sessionId, userId, service, data);
    }

    /**
     * 创建心跳超时事件
     */
    public static <T> WebSocketEvent<T> onHeartbeatTimeout(String sessionId, String userId, String service, T data) {
        return new WebSocketEvent<>(WebSocketEventType.ON_HEARTBEAT_TIMEOUT, sessionId, userId, service, data);
    }

    /**
     * 创建自定义事件
     */
    public static <T> WebSocketEvent<T> custom(String sessionId, String userId, String service, T data) {
        return new WebSocketEvent<>(WebSocketEventType.CUSTOM, sessionId, userId, service, data);
    }
}