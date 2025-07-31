package com.framework.websocket.handler;

import com.framework.websocket.event.WebSocketEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基础WebSocket消息处理器
 * 处理基本的WebSocket事件，提供默认实现
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class DefaultWebSocketMessageHandler implements WebSocketMessageHandler<Object> {

    @Override
    public Object handleEvent(WebSocketEvent<Object> event) {
        // 添加空值检查
        if (event == null) {
            log.error("WebSocketEvent为null，无法处理");
            return null;
        }
        
        if (event.getEventType() == null) {
            log.error("WebSocketEvent的eventType为null: sessionId={}, service={}, userId={}", 
                event.getSessionId(), event.getService(), event.getUserId());
            return null;
        }
        
        switch (event.getEventType()) {
            case ON_OPEN:
                handleOnOpen(event);
                break;
            case ON_CLOSE:
                handleOnClose(event);
                break;
            case ON_MESSAGE:
                handleOnMessage(event);
                break;
            case ON_SEND:
                handleOnSend(event);
                break;
            case ON_ERROR:
                handleOnError(event);
                break;
            case ON_HEARTBEAT:
                handleOnHeartbeat(event);
                break;
            case ON_HEARTBEAT_TIMEOUT:
                handleOnHeartbeatTimeout(event);
                break;
            default:
                handleCustomEvent(event);
                break;
        }
        return null;
    }

    /**
     * 处理连接建立事件
     */
    protected void handleOnOpen(WebSocketEvent<Object> event) {
        log.info("用户连接建立: service={}, userId={}, sessionId={}, clientIp={}", 
                event.getService(), event.getUserId(), event.getSessionId(), event.getClientIp());
    }

    /**
     * 处理连接关闭事件
     */
    protected void handleOnClose(WebSocketEvent<Object> event) {
        log.info("用户连接关闭: service={}, userId={}, sessionId={}, data={}", 
                event.getService(), event.getUserId(), event.getSessionId(), event.getData());
    }

    /**
     * 处理消息接收事件
     */
    protected void handleOnMessage(WebSocketEvent<Object> event) {
        log.info("接收到用户消息: service={}, userId={}, sessionId={}, message={}", 
                event.getService(), event.getUserId(), event.getSessionId(), event.getData());
    }

    /**
     * 处理消息发送事件
     */
    protected void handleOnSend(WebSocketEvent<Object> event) {
        log.debug("消息发送完成: service={}, userId={}, sessionId={}, message={}", 
                event.getService(), event.getUserId(), event.getSessionId(), event.getData());
    }

    /**
     * 处理错误事件
     */
    protected void handleOnError(WebSocketEvent<Object> event) {
        log.error("WebSocket发生错误: service={}, userId={}, sessionId={}, error={}", 
                event.getService(), event.getUserId(), event.getSessionId(), event.getErrorMessage());
        
        if (event.getThrowable() != null) {
            log.error("错误详情:", event.getThrowable());
        }
    }

    /**
     * 处理心跳事件
     */
    protected void handleOnHeartbeat(WebSocketEvent<Object> event) {
        log.debug("收到心跳: service={}, userId={}, sessionId={}", 
                event.getService(), event.getUserId(), event.getSessionId());
    }

    /**
     * 处理心跳超时事件
     */
    protected void handleOnHeartbeatTimeout(WebSocketEvent<Object> event) {
        log.warn("心跳超时: service={}, userId={}, sessionId={}", 
                event.getService(), event.getUserId(), event.getSessionId());
    }

    /**
     * 处理自定义事件
     */
    protected void handleCustomEvent(WebSocketEvent<Object> event) {
        log.info("收到自定义事件: eventType={}, service={}, userId={}, sessionId={}, data={}", 
                event.getEventType(), event.getService(), event.getUserId(), event.getSessionId(), event.getData());
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE; // 最低优先级，确保在其他处理器之后执行
    }
}