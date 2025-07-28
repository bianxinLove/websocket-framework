package com.framework.websocket.event;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;

/**
 * WebSocket事件总线
 * 基于Google Guava EventBus实现异步事件分发
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class WebSocketEventBus implements SubscriberExceptionHandler {

    private final ExecutorService executorService;
    /**
     * -- GETTER --
     *  获取EventBus实例（供测试使用）
     */
    @Getter
    private EventBus eventBus;

    @Autowired
    public WebSocketEventBus(@Qualifier("webSocketExecutorService") ExecutorService executorService) {
        this.executorService = executorService;
    }

    @PostConstruct
    public void initialize() {
        this.eventBus = new AsyncEventBus("WebSocketEventBus", executorService);
        log.info("WebSocket事件总线初始化完成");
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            log.info("WebSocket事件总线线程池已关闭");
        }
    }

    /**
     * 发布事件
     */
    public void post(Object event) {
        try {
            eventBus.post(event);
            if (log.isDebugEnabled()) {
                log.debug("WebSocket事件已发布: {}", event);
            }
        } catch (Exception e) {
            log.error("发布WebSocket事件失败: {}", event, e);
        }
    }

    /**
     * 注册订阅者
     */
    public synchronized void register(Object subscriber) {
        try {
            eventBus.register(subscriber);
            log.info("WebSocket事件订阅者注册成功: {}", subscriber.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("注册WebSocket事件订阅者失败: {}", subscriber.getClass().getSimpleName(), e);
            throw e;
        }
    }

    /**
     * 取消注册订阅者
     */
    public void unregister(Object subscriber) {
        try {
            eventBus.unregister(subscriber);
            log.info("WebSocket事件订阅者取消注册成功: {}", subscriber.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("取消注册WebSocket事件订阅者失败: {}", subscriber.getClass().getSimpleName(), e);
        }
    }

    /**
     * 处理订阅者异常
     */
    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
        log.error("WebSocket事件订阅者处理事件时发生异常 - 事件: {}, 订阅者: {}, 方法: {}", 
                context.getEvent(), 
                context.getSubscriber().getClass().getSimpleName(),
                context.getSubscriberMethod().getName(),
                exception);
    }

}