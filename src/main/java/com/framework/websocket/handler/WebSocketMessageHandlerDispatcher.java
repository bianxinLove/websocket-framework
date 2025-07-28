package com.framework.websocket.handler;

import com.framework.websocket.event.WebSocketEvent;
import com.framework.websocket.event.WebSocketEventBus;
import com.framework.websocket.interceptor.WebSocketEventInterceptor;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebSocket消息处理器调度器
 * 负责将EventBus中的事件分发给相应的WebSocketMessageHandler处理
 * 支持拦截器链和优先级处理
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class WebSocketMessageHandlerDispatcher {

    @Autowired
    private WebSocketEventBus eventBus;

    @Autowired(required = false)
    private List<WebSocketMessageHandler<?>> messageHandlers;

    @Autowired(required = false)
    private List<WebSocketEventInterceptor> interceptors;

    @PostConstruct
    public void initialize() {
        // 将自己注册为EventBus的订阅者
        eventBus.register(this);
        
        log.info("WebSocketMessageHandlerDispatcher初始化完成, 注册了{}个消息处理器, {}个拦截器", 
                messageHandlers != null ? messageHandlers.size() : 0,
                interceptors != null ? interceptors.size() : 0);
    }

    @PreDestroy
    public void destroy() {
        // 取消EventBus订阅
        try {
            eventBus.unregister(this);
            log.info("WebSocketMessageHandlerDispatcher已取消EventBus订阅");
        } catch (Exception e) {
            log.warn("取消EventBus订阅时出现异常", e);
        }
    }

    /**
     * 监听WebSocket事件并分发给相应的处理器
     */
    @Subscribe
    public void handleWebSocketEvent(WebSocketEvent<?> event) {
        if (messageHandlers == null || messageHandlers.isEmpty()) {
            log.debug("没有可用的消息处理器，跳过事件处理: {}", event.getEventType());
            return;
        }

        // 按优先级排序拦截器
        List<WebSocketEventInterceptor> sortedInterceptors = getSortedInterceptors();

        // 找到支持此事件的处理器并按优先级排序
        List<WebSocketMessageHandler<?>> supportedHandlers = messageHandlers.stream()
                .filter(handler -> supportsEvent(handler, event))
                .sorted(Comparator.comparing(WebSocketMessageHandler::getPriority))
                .collect(Collectors.toList());

        if (supportedHandlers.isEmpty()) {
            log.debug("没有找到支持此事件的处理器: eventType={}, service={}", 
                    event.getEventType(), event.getService());
            return;
        }

        log.debug("找到{}个支持此事件的处理器: eventType={}, service={}", 
                supportedHandlers.size(), event.getEventType(), event.getService());

        // 依次处理每个支持的处理器
        for (WebSocketMessageHandler<?> handler : supportedHandlers) {
            processWithHandler(handler, event, sortedInterceptors);
        }
    }

    /**
     * 使用指定处理器处理事件，包含拦截器链
     */
    @SuppressWarnings("unchecked")
    private void processWithHandler(WebSocketMessageHandler<?> handler, WebSocketEvent<?> event, 
                                   List<WebSocketEventInterceptor> interceptors) {
        Object result = null;
        Exception exception = null;
        
        try {
            // 前置拦截器处理
            if (!applyPreInterceptors(interceptors, event)) {
                log.debug("前置拦截器中断了事件处理: handler={}, eventType={}", 
                        handler.getClass().getSimpleName(), event.getEventType());
                return;
            }

            // 执行实际的事件处理
            result = ((WebSocketMessageHandler<Object>) handler).handleEvent((WebSocketEvent<Object>) event);
            
            log.debug("事件处理完成: handler={}, eventType={}, service={}, result={}", 
                    handler.getClass().getSimpleName(), event.getEventType(), event.getService(), result);

            // 后置拦截器处理
            applyPostInterceptors(interceptors, event, result);

        } catch (Exception e) {
            exception = e;
            log.error("事件处理异常: handler={}, eventType={}, service={}", 
                    handler.getClass().getSimpleName(), event.getEventType(), event.getService(), e);
        } finally {
            // 完成拦截器处理
            applyAfterCompletionInterceptors(interceptors, event, result, exception);
        }
    }

    /**
     * 检查处理器是否支持指定事件
     */
    private boolean supportsEvent(WebSocketMessageHandler<?> handler, WebSocketEvent<?> event) {
        try {
            return handler.supports((WebSocketEvent) event);
        } catch (Exception e) {
            log.warn("检查处理器支持性时发生异常: handler={}, eventType={}", 
                    handler.getClass().getSimpleName(), event.getEventType(), e);
            return false;
        }
    }

    /**
     * 获取按优先级排序的拦截器列表
     */
    private List<WebSocketEventInterceptor> getSortedInterceptors() {
        if (interceptors == null) {
            return new ArrayList<>();
        }
        return interceptors.stream()
                .sorted(Comparator.comparing(WebSocketEventInterceptor::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * 应用前置拦截器
     */
    private boolean applyPreInterceptors(List<WebSocketEventInterceptor> interceptors, WebSocketEvent<?> event) {
        for (WebSocketEventInterceptor interceptor : interceptors) {
            try {
                if (!interceptor.preHandle(event)) {
                    return false;
                }
            } catch (Exception e) {
                log.error("前置拦截器处理异常: interceptor={}, eventType={}", 
                        interceptor.getClass().getSimpleName(), event.getEventType(), e);
                return false;
            }
        }
        return true;
    }

    /**
     * 应用后置拦截器
     */
    private void applyPostInterceptors(List<WebSocketEventInterceptor> interceptors, 
                                      WebSocketEvent<?> event, Object result) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            WebSocketEventInterceptor interceptor = interceptors.get(i);
            try {
                interceptor.postHandle(event, result);
            } catch (Exception e) {
                log.error("后置拦截器处理异常: interceptor={}, eventType={}", 
                        interceptor.getClass().getSimpleName(), event.getEventType(), e);
            }
        }
    }

    /**
     * 应用完成拦截器
     */
    private void applyAfterCompletionInterceptors(List<WebSocketEventInterceptor> interceptors, 
                                                 WebSocketEvent<?> event, Object result, Exception ex) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            WebSocketEventInterceptor interceptor = interceptors.get(i);
            try {
                interceptor.afterCompletion(event, result, ex);
            } catch (Exception e) {
                log.error("完成拦截器处理异常: interceptor={}, eventType={}", 
                        interceptor.getClass().getSimpleName(), event.getEventType(), e);
            }
        }
    }

    /**
     * 获取当前注册的消息处理器数量
     */
    public int getHandlerCount() {
        return messageHandlers != null ? messageHandlers.size() : 0;
    }

    /**
     * 获取当前注册的拦截器数量
     */
    public int getInterceptorCount() {
        return interceptors != null ? interceptors.size() : 0;
    }
}