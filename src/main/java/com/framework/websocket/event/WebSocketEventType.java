package com.framework.websocket.event;

/**
 * WebSocket事件类型枚举
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
public enum WebSocketEventType {

    /**
     * 连接建立事件
     */
    ON_OPEN("onOpen", "连接建立"),

    /**
     * 连接关闭事件
     */
    ON_CLOSE("onClose", "连接关闭"),

    /**
     * 接收消息事件
     */
    ON_MESSAGE("onMessage", "接收消息"),

    /**
     * 发送消息事件
     */
    ON_SEND("onSend", "发送消息"),

    /**
     * 错误事件
     */
    ON_ERROR("onError", "发生错误"),

    /**
     * 心跳事件
     */
    ON_HEARTBEAT("onHeartbeat", "心跳检测"),

    /**
     * 心跳超时事件
     */
    ON_HEARTBEAT_TIMEOUT("onHeartbeatTimeout", "心跳超时"),

    /**
     * 认证成功事件
     */
    ON_AUTH_SUCCESS("onAuthSuccess", "认证成功"),

    /**
     * 认证失败事件
     */
    ON_AUTH_FAILURE("onAuthFailure", "认证失败"),

    /**
     * 自定义事件
     */
    CUSTOM("custom", "自定义事件");

    private final String eventCode;
    private final String eventName;

    WebSocketEventType(String eventCode, String eventName) {
        this.eventCode = eventCode;
        this.eventName = eventName;
    }

    public String getEventCode() {
        return eventCode;
    }

    public String getEventName() {
        return eventName;
    }

    /**
     * 根据事件代码获取事件类型
     */
    public static WebSocketEventType fromCode(String eventCode) {
        for (WebSocketEventType type : values()) {
            if (type.eventCode.equals(eventCode)) {
                return type;
            }
        }
        return CUSTOM;
    }
}