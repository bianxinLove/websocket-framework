package com.framework.websocket.example;

import com.framework.websocket.annotation.WebSocketService;
import com.framework.websocket.event.WebSocketEvent;
import com.framework.websocket.handler.WebSocketMessageHandler;
import com.framework.websocket.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 聊天室WebSocket服务示例
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
@WebSocketService(value = "chatroom", name = "聊天室服务", description = "基于WebSocket的聊天室功能")
public class ChatRoomWebSocketService implements WebSocketMessageHandler<String> {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Override
    public Object handleEvent(WebSocketEvent<String> event) {
        switch (event.getEventType()) {
            case ON_OPEN:
                handleUserJoin(event);
                break;
            case ON_CLOSE:
                handleUserLeave(event);
                break;
            case ON_MESSAGE:
                handleChatMessage(event);
                break;
            case ON_ERROR:
                handleError(event);
                break;
            default:
                break;
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 100;
    }
    
    // 注意：getSupportedServices()方法已移除，现在通过@WebSocketService注解自动获取服务支持列表

    /**
     * 处理用户加入聊天室
     */
    private void handleUserJoin(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        String welcomeMessage = String.format("用户 %s 加入了聊天室", userId);
        
        // 广播欢迎消息给所有用户
        sessionManager.broadcast("chatroom", createSystemMessage(welcomeMessage));
        
        // 发送当前在线用户列表给新用户
        int onlineCount = sessionManager.getOnlineCount("chatroom");
        String onlineMessage = String.format("当前在线用户数: %d", onlineCount);
        sessionManager.sendMessage("chatroom", userId, createSystemMessage(onlineMessage));
        
        log.info("用户加入聊天室: userId={}, 当前在线数: {}", userId, onlineCount);
    }

    /**
     * 处理用户离开聊天室
     */
    private void handleUserLeave(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        String leaveMessage = String.format("用户 %s 离开了聊天室", userId);
        
        // 广播离开消息给所有用户
        sessionManager.broadcast("chatroom", createSystemMessage(leaveMessage));
        
        int onlineCount = sessionManager.getOnlineCount("chatroom");
        log.info("用户离开聊天室: userId={}, 当前在线数: {}", userId, onlineCount);
    }

    /**
     * 处理聊天消息
     */
    private void handleChatMessage(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        String message = event.getData();
        
        // 构造聊天消息
        String chatMessage = createChatMessage(userId, message);
        
        // 广播消息给所有用户
        sessionManager.broadcast("chatroom", chatMessage);
        
        log.info("收到聊天消息: userId={}, message={}", userId, message);
    }

    /**
     * 处理错误
     */
    private void handleError(WebSocketEvent<String> event) {
        log.error("聊天室发生错误: userId={}, error={}", event.getUserId(), event.getErrorMessage());
    }

    /**
     * 创建系统消息
     */
    private String createSystemMessage(String message) {
        return String.format("{\"type\":\"system\",\"message\":\"系统消息: %s\",\"timestamp\":%d}", 
                message, System.currentTimeMillis());
    }

    /**
     * 创建聊天消息
     */
    private String createChatMessage(String userId, String message) {
        return String.format("{\"type\":\"chat\",\"userId\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}", 
                userId, message, System.currentTimeMillis());
    }

    /**
     * 发送私聊消息
     */
    public boolean sendPrivateMessage(String fromUserId, String toUserId, String message) {
        String privateMessage = String.format(
                "{\"type\":\"private\",\"fromUserId\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}", 
                fromUserId, message, System.currentTimeMillis());
        
        boolean sent = sessionManager.sendMessage("chatroom", toUserId, privateMessage);
        if (sent) {
            log.info("私聊消息发送成功: from={}, to={}, message={}", fromUserId, toUserId, message);
        } else {
            log.warn("私聊消息发送失败，用户不在线: from={}, to={}", fromUserId, toUserId);
        }
        return sent;
    }

    /**
     * 获取在线用户列表
     */
    public void sendOnlineUserList(String userId) {
        int onlineCount = sessionManager.getOnlineCount("chatroom");
        String onlineUsersMessage = String.format(
                "{\"type\":\"onlineUsers\",\"count\":%d,\"timestamp\":%d}", 
                onlineCount, System.currentTimeMillis());
        
        sessionManager.sendMessage("chatroom", userId, onlineUsersMessage);
    }
}