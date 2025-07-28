package com.framework.websocket.annotation;

import java.lang.annotation.*;

/**
 * WebSocket服务注解
 * 标记WebSocket业务服务类
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebSocketService {

    /**
     * 服务标识
     */
    String value();

    /**
     * 服务名称
     */
    String name() default "";

    /**
     * 服务描述
     */
    String description() default "";

    /**
     * 是否启用
     */
    boolean enabled() default true;
}