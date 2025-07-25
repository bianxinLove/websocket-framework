package com.framework.websocket.interceptor;

import com.framework.websocket.event.WebSocketEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket日志拦截器
 * 记录WebSocket事件的详细日志
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
@Slf4j
@Component
public class LoggingWebSocketEventInterceptor implements WebSocketEventInterceptor {

    @Override
    public boolean preHandle(WebSocketEvent<?> event) {
        if (log.isDebugEnabled()) {
            log.debug("WebSocket事件开始处理: eventType={}, service={}, userId={}, sessionId={}", 
                    event.getEventType(), event.getService(), event.getUserId(), event.getSessionId());
        }
        return true;
    }

    @Override
    public void postHandle(WebSocketEvent<?> event, Object result) {
        if (log.isDebugEnabled()) {
            log.debug("WebSocket事件处理完成: eventType={}, service={}, userId={}, sessionId={}, result={}", 
                    event.getEventType(), event.getService(), event.getUserId(), event.getSessionId(), result);
        }
    }

    @Override
    public void afterCompletion(WebSocketEvent<?> event, Object result, Exception ex) {
        if (ex != null) {
            log.error("WebSocket事件处理异常: eventType={}, service={}, userId={}, sessionId={}", 
                    event.getEventType(), event.getService(), event.getUserId(), event.getSessionId(), ex);
        } else if (log.isDebugEnabled()) {
            log.debug("WebSocket事件处理最终完成: eventType={}, service={}, userId={}, sessionId={}", 
                    event.getEventType(), event.getService(), event.getUserId(), event.getSessionId());
        }
    }

    @Override
    public int getOrder() {
        return -1000; // 高优先级，确保日志记录在最外层
    }
}