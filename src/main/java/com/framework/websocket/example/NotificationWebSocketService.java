package com.framework.websocket.example;

import com.framework.websocket.annotation.WebSocketService;
import com.framework.websocket.event.WebSocketEvent;
import com.framework.websocket.handler.WebSocketMessageHandler;
import com.framework.websocket.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 通知推送WebSocket服务示例
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
@Slf4j
@Component
@WebSocketService(value = "notification", name = "通知推送服务", description = "实时通知推送功能")
public class NotificationWebSocketService implements WebSocketMessageHandler<String> {

    @Autowired
    private WebSocketSessionManager sessionManager;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public Object handleEvent(WebSocketEvent<String> event) {
        switch (event.getEventType()) {
            case ON_OPEN:
                handleUserConnect(event);
                break;
            case ON_CLOSE:
                handleUserDisconnect(event);
                break;
            case ON_MESSAGE:
                handleMessage(event);
                break;
            default:
                break;
        }
        return null;
    }

    @Override
    public String[] getSupportedServices() {
        return new String[]{"notification"};
    }

    @Override
    public int getPriority() {
        return 200;
    }

    /**
     * 处理用户连接
     */
    private void handleUserConnect(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        
        // 发送欢迎消息
        String welcomeMessage = createNotification("系统通知", "欢迎使用通知推送服务", "info");
        sessionManager.sendMessage("notification", userId, welcomeMessage);
        
        // 启动定时推送示例（每30秒推送一次）
        startPeriodicNotification(userId);
        
        log.info("用户连接通知服务: userId={}", userId);
    }

    /**
     * 处理用户断开连接
     */
    private void handleUserDisconnect(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        log.info("用户断开通知服务: userId={}", userId);
    }

    /**
     * 处理用户消息
     */
    private void handleMessage(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        String message = event.getData();
        
        // 简单的命令处理
        if ("ping".equalsIgnoreCase(message)) {
            String pongMessage = createNotification("系统响应", "pong", "success");
            sessionManager.sendMessage("notification", userId, pongMessage);
        } else if ("status".equalsIgnoreCase(message)) {
            int onlineCount = sessionManager.getOnlineCount("notification");
            String statusMessage = createNotification("状态信息", 
                    "通知服务运行正常，当前在线用户数: " + onlineCount, "info");
            sessionManager.sendMessage("notification", userId, statusMessage);
        }
        
        log.info("收到通知服务消息: userId={}, message={}", userId, message);
    }

    /**
     * 发送个人通知
     */
    public boolean sendPersonalNotification(String userId, String title, String content, String type) {
        String notification = createNotification(title, content, type);
        boolean sent = sessionManager.sendMessage("notification", userId, notification);
        
        if (sent) {
            log.info("个人通知发送成功: userId={}, title={}", userId, title);
        } else {
            log.warn("个人通知发送失败，用户不在线: userId={}", userId);
        }
        
        return sent;
    }

    /**
     * 广播通知给所有用户
     */
    public void broadcastNotification(String title, String content, String type) {
        String notification = createNotification(title, content, type);
        sessionManager.broadcast("notification", notification);
        
        int onlineCount = sessionManager.getOnlineCount("notification");
        log.info("广播通知发送完成: title={}, 接收用户数={}", title, onlineCount);
    }

    /**
     * 发送紧急通知
     */
    public void sendUrgentNotification(String title, String content) {
        String urgentNotification = createNotification(title, content, "urgent");
        sessionManager.broadcast("notification", urgentNotification);
        
        log.warn("紧急通知已发送: title={}", title);
    }

    /**
     * 启动定时推送
     */
    private void startPeriodicNotification(String userId) {
        scheduler.scheduleAtFixedRate(() -> {
            if (sessionManager.isOnline("notification", userId)) {
                String timeNotification = createNotification("定时推送", 
                        "这是一个定时推送消息: " + System.currentTimeMillis(), "info");
                sessionManager.sendMessage("notification", userId, timeNotification);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 创建通知消息
     */
    private String createNotification(String title, String content, String type) {
        return String.format(
                "{\"type\":\"notification\",\"title\":\"%s\",\"content\":\"%s\",\"level\":\"%s\",\"timestamp\":%d}", 
                title, content, type, System.currentTimeMillis());
    }

    /**
     * 创建系统维护通知
     */
    public void sendMaintenanceNotification(String maintenanceTime) {
        String notification = createNotification("系统维护通知", 
                "系统将于 " + maintenanceTime + " 进行维护，请提前保存工作", "warning");
        sessionManager.broadcast("notification", notification);
        
        log.info("系统维护通知已发送: maintenanceTime={}", maintenanceTime);
    }
}