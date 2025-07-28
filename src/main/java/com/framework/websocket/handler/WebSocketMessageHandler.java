package com.framework.websocket.handler;

import com.framework.websocket.event.WebSocketEvent;

/**
 * WebSocket消息处理器接口
 * 定义处理WebSocket事件的标准接口
 * 
 * @author bianxin
 * @version 1.0.0
 */
public interface WebSocketMessageHandler<T> {

    /**
     * 处理WebSocket事件
     * 
     * @param event WebSocket事件
     * @return 处理结果，可以为null
     */
    Object handleEvent(WebSocketEvent<T> event);

    /**
     * 获取处理器支持的事件类型
     * 返回null或空数组表示支持所有事件类型
     */
    default String[] getSupportedEventTypes() {
        return null;
    }

    /**
     * 获取处理器支持的服务标识
     * 返回null或空数组表示支持所有服务
     */
    default String[] getSupportedServices() {
        return null;
    }

    /**
     * 获取处理器优先级
     * 数值越小优先级越高
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 检查是否支持指定的事件
     */
    default boolean supports(WebSocketEvent<T> event) {
        // 检查事件类型
        String[] supportedEventTypes = getSupportedEventTypes();
        if (supportedEventTypes != null && supportedEventTypes.length > 0) {
            boolean eventTypeSupported = false;
            String eventType = event.getEventType().getEventCode();
            for (String supportedType : supportedEventTypes) {
                if (supportedType.equals(eventType)) {
                    eventTypeSupported = true;
                    break;
                }
            }
            if (!eventTypeSupported) {
                return false;
            }
        }

        // 检查服务标识
        String[] supportedServices = getSupportedServices();
        if (supportedServices != null && supportedServices.length > 0) {
            boolean serviceSupported = false;
            String service = event.getService();
            for (String supportedService : supportedServices) {
                if (supportedService.equals(service)) {
                    serviceSupported = true;
                    break;
                }
            }
            return serviceSupported;
        }

        return true;
    }
}