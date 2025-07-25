package com.framework.websocket.annotation;

import java.lang.annotation.*;

/**
 * WebSocket事件监听器注解
 * 标记WebSocket事件处理方法
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebSocketEventListener {

    /**
     * 监听的事件类型
     * 空数组表示监听所有事件
     */
    String[] eventTypes() default {};

    /**
     * 监听的服务标识
     * 空数组表示监听所有服务
     */
    String[] services() default {};

    /**
     * 是否异步处理
     */
    boolean async() default true;

    /**
     * 处理优先级（数值越小优先级越高）
     */
    int priority() default 0;

    /**
     * 描述信息
     */
    String description() default "";
}