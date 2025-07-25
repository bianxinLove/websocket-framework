package com.framework.websocket.interceptor;

import com.framework.websocket.event.WebSocketEvent;

/**
 * WebSocket事件拦截器接口
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
public interface WebSocketEventInterceptor {

    /**
     * 事件处理前拦截
     * 
     * @param event WebSocket事件
     * @return true继续处理，false中断处理
     */
    default boolean preHandle(WebSocketEvent<?> event) {
        return true;
    }

    /**
     * 事件处理后拦截
     * 
     * @param event WebSocket事件
     * @param result 处理结果
     */
    default void postHandle(WebSocketEvent<?> event, Object result) {
        // 默认空实现
    }

    /**
     * 事件处理完成后拦截（包括异常情况）
     * 
     * @param event WebSocket事件
     * @param result 处理结果
     * @param ex 异常信息（如果有）
     */
    default void afterCompletion(WebSocketEvent<?> event, Object result, Exception ex) {
        // 默认空实现
    }

    /**
     * 获取拦截器优先级
     * 数值越小优先级越高
     */
    default int getOrder() {
        return 0;
    }
}