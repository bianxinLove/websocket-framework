package com.framework.websocket.example;

import com.framework.websocket.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket测试控制器
 * 提供HTTP接口用于测试WebSocket功能
 *
 * @author WebSocket Framework
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/websocket/test")
public class WebSocketTestController {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Autowired
    private ChatRoomWebSocketService chatRoomService;

    @Autowired
    private NotificationWebSocketService notificationService;

    /**
     * 获取在线用户统计
     */
    @GetMapping("/stats")
    public Map<String, Object> getOnlineStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalOnline", sessionManager.getTotalOnlineCount());
        result.put("chatroomOnline", sessionManager.getOnlineCount("chatroom"));
        result.put("notificationOnline", sessionManager.getOnlineCount("notification"));
        return result;
    }


    /**
     * 获取指定服务的在线用户列表
     */
    @GetMapping("/online/{service}")
    public Set<String> getOnlineUsers(@PathVariable String service) {
        return sessionManager.getOnlineUsers(service);
    }

    /**
     * 发送消息给指定用户
     */
    @PostMapping("/send")
    public Map<String, Object> sendMessage(
            @RequestParam String service,
            @RequestParam String userId,
            @RequestParam String message) {

        boolean sent = sessionManager.sendMessage(service, userId, message);
        Map<String, Object> result = new HashMap<>();
        result.put("success", sent);
        result.put("message", sent ? "消息发送成功" : "用户不在线或发送失败");
        return result;
    }


    /**
     * 广播消息给指定服务的所有用户
     */
    @PostMapping("/broadcast")
    public Map<String, Object> broadcastMessage(
            @RequestParam String service,
            @RequestParam String message) {

        int onlineCount = sessionManager.getOnlineCount(service);
        sessionManager.broadcast(service, message);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("recipients", onlineCount);
        result.put("message", "广播消息发送完成");
        return result;
    }


    /**
     * 发送聊天室私聊消息
     */
    @PostMapping("/chatroom/private")
    public Map<String, Object> sendPrivateMessage(
            @RequestParam String fromUserId,
            @RequestParam String toUserId,
            @RequestParam String message) {

        boolean sent = chatRoomService.sendPrivateMessage(fromUserId, toUserId, message);
        Map<String, Object> result = new HashMap<>();
        result.put("success", sent);
        result.put("message", sent ? "消息发送成功" : "用户不在线或发送失败");
        return result;
    }

    /**
     * 发送个人通知
     */
    @PostMapping("/notification/personal")
    public Map<String, Object> sendPersonalNotification(
            @RequestParam String userId,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "info") String type) {

        boolean sent = notificationService.sendPersonalNotification(userId, title, content, type);
        Map<String, Object> result = new HashMap<>();
        result.put("success", sent);
        result.put("message", sent ? "个人通知发送成功" : "用户不在线");
        return result;
    }

    /**
     * 广播通知
     */
    @PostMapping("/notification/broadcast")
    public Map<String, Object> broadcastNotification(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(defaultValue = "info") String type) {

        int onlineCount = sessionManager.getOnlineCount("notification");
        notificationService.broadcastNotification(title, content, type);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("recipients", onlineCount);
        result.put("message", "广播通知发送完成");
        return result;
    }

    /**
     * 发送紧急通知
     */
    @PostMapping("/notification/urgent")
    public Map<String, Object> sendUrgentNotification(
            @RequestParam String title,
            @RequestParam String content) {

        int onlineCount = sessionManager.getOnlineCount("notification");
        notificationService.sendUrgentNotification(title, content);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("recipients", onlineCount);
        result.put("message", "紧急通知发送完成");
        return result;
    }

    /**
     * 发送系统维护通知
     */
    @PostMapping("/notification/maintenance")
    public Map<String, Object> sendMaintenanceNotification(
            @RequestParam String maintenanceTime) {

        int onlineCount = sessionManager.getOnlineCount("notification");
        notificationService.sendMaintenanceNotification(maintenanceTime);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("recipients", onlineCount);
        result.put("message", "系统维护通知发送完成");
        return result;
    }
}