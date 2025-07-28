package com.framework.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * WebSocket框架启动类
 * 
 * @author bianxin
 * @version 1.0.0
 */
@SpringBootApplication
@EnableConfigurationProperties
public class WebSocketFrameworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketFrameworkApplication.class, args);
        System.out.println("\n" +
                "=======================================================\n" +
                "    WebSocket框架启动成功!\n" +
                "    \n" +
                "    WebSocket连接地址:\n" +
                "    - 聊天室: ws://localhost:8080/websocket/connect/chatroom/{userId}\n" +
                "    - 通知推送: ws://localhost:8080/websocket/connect/notification/{userId}\n" +
                "    \n" +
                "    示例页面: http://localhost:8080/test.html\n" +
                "    管理API: http://localhost:8080/websocket/admin/health\n" +
                "    指标监控: http://localhost:8080/websocket/admin/metrics\n" +
                "=======================================================\n");
    }
}