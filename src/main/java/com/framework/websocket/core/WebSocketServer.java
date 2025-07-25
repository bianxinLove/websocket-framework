package com.framework.websocket.core;

import com.framework.websocket.event.WebSocketEvent;
import com.framework.websocket.event.WebSocketEventBus;
import com.framework.websocket.session.WebSocketSession;
import com.framework.websocket.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket服务器核心类
 * 处理WebSocket连接的生命周期事件
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
@ServerEndpoint(
    value = "/websocket/connect/{service}/{userId}", 
    configurator = WebSocketEndpointConfig.class
)
@Component
@Slf4j
public class WebSocketServer {

    /**
     * 心跳间隔时间（秒）
     */
    private static final int HEARTBEAT_INTERVAL = WebSocketConstants.DEFAULT_HEARTBEAT_INTERVAL;

    /**
     * 心跳超时时间（秒）
     */
    private static final int HEARTBEAT_TIMEOUT = WebSocketConstants.DEFAULT_HEARTBEAT_TIMEOUT;

    /**
     * 下次心跳超时时间戳
     */
    private final AtomicLong nextHeartbeatTimeout = new AtomicLong(0);

    /**
     * WebSocket会话
     */
    private WebSocketSession webSocketSession;

    /**
     * 静态依赖注入（WebSocket端点实例化机制要求）
     */
    private static WebSocketSessionManager sessionManager;
    private static WebSocketEventBus eventBus;
    private static ScheduledExecutorService scheduledExecutorService;

    @Autowired
    public void setSessionManager(WebSocketSessionManager sessionManager) {
        WebSocketServer.sessionManager = sessionManager;
    }

    @Autowired
    public void setEventBus(WebSocketEventBus eventBus) {
        WebSocketServer.eventBus = eventBus;
    }

    @Autowired
    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        WebSocketServer.scheduledExecutorService = scheduledExecutorService;
    }

    /**
     * 连接建立事件
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("service") String service, @PathParam("userId") String userId) {
        try {
            // 创建WebSocket会话包装
            this.webSocketSession = new WebSocketSession(session, userId, service);
            
            // 添加到会话管理器
            sessionManager.addSession(service, userId, webSocketSession);
            
            // 启动心跳检测
            startHeartbeat();
            
            // 发布连接建立事件
            WebSocketEvent<String> event = WebSocketEvent.onOpen(
                session.getId(), userId, service, "连接建立成功"
            );
            event.setClientIp(webSocketSession.getClientIp());
            eventBus.post(event);
            
            log.info("WebSocket连接建立: service={}, userId={}, sessionId={}, clientIp={}", 
                    service, userId, session.getId(), webSocketSession.getClientIp());
                    
        } catch (Exception e) {
            log.error("WebSocket连接建立失败: service={}, userId={}", service, userId, e);
            
            // 发布错误事件
            WebSocketEvent<String> errorEvent = WebSocketEvent.onError(
                session.getId(), userId, service, "连接建立失败: " + e.getMessage(), e
            );
            eventBus.post(errorEvent);
        }
    }

    /**
     * 连接关闭事件
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        if (webSocketSession == null) {
            log.warn("WebSocket会话为空，无法处理关闭事件: sessionId={}", session.getId());
            return;
        }

        String service = webSocketSession.getService();
        String userId = webSocketSession.getUserId();
        
        try {
            // 判断是否为异常关闭
            if (closeReason.getCloseCode() == CloseReason.CloseCodes.CLOSED_ABNORMALLY) {
                log.warn("WebSocket异常关闭: service={}, userId={}, sessionId={}, reason={}", 
                        service, userId, session.getId(), closeReason.getReasonPhrase());
            } else {
                // 正常关闭时移除会话
                sessionManager.removeSession(service, userId, webSocketSession);
            }
            
            // 发布连接关闭事件
            WebSocketEvent<CloseReason> event = WebSocketEvent.onClose(
                session.getId(), userId, service, closeReason
            );
            event.setClientIp(webSocketSession.getClientIp());
            eventBus.post(event);
            
            log.info("WebSocket连接关闭: service={}, userId={}, sessionId={}, code={}, reason={}", 
                    service, userId, session.getId(), closeReason.getCloseCode(), closeReason.getReasonPhrase());
                    
        } catch (Exception e) {
            log.error("WebSocket连接关闭处理失败: service={}, userId={}", service, userId, e);
        }
    }

    /**
     * 接收文本消息事件
     */
    @OnMessage
    public void onMessage(String message) {
        if (webSocketSession == null) {
            log.warn("WebSocket会话为空，无法处理消息: message={}", message);
            return;
        }

        String service = webSocketSession.getService();
        String userId = webSocketSession.getUserId();
        
        try {
            // 更新接收消息计数
            webSocketSession.incrementReceiveCount();
            
            // 发布消息接收事件
            WebSocketEvent<String> event = WebSocketEvent.onMessage(
                webSocketSession.getSessionId(), userId, service, message
            );
            event.setClientIp(webSocketSession.getClientIp());
            eventBus.post(event);
            
            log.info("接收到WebSocket消息: service={}, userId={}, sessionId={}, message={}", 
                    service, userId, webSocketSession.getSessionId(), message);
                    
        } catch (Exception e) {
            log.error("WebSocket消息处理失败: service={}, userId={}, message={}", service, userId, message, e);
        }
    }

    /**
     * 接收Pong消息事件（心跳响应）
     */
    @OnMessage
    public void onPong(PongMessage pong) {
        if (webSocketSession == null) {
            log.warn("WebSocket会话为空，无法处理心跳响应");
            return;
        }

        String service = webSocketSession.getService();
        String userId = webSocketSession.getUserId();
        
        try {
            // 更新心跳时间
            webSocketSession.updateHeartbeat();
            nextHeartbeatTimeout.set(System.currentTimeMillis() + HEARTBEAT_TIMEOUT * 1000L);
            
            // 更新会话管理器中的心跳状态
            sessionManager.updateHeartbeat(service, userId, HEARTBEAT_TIMEOUT);
            
            // 发布心跳事件
            WebSocketEvent<String> event = WebSocketEvent.onHeartbeat(
                webSocketSession.getSessionId(), userId, service, "心跳响应"
            );
            eventBus.post(event);
            
            log.debug("接收到WebSocket心跳响应: service={}, userId={}, sessionId={}", 
                    service, userId, webSocketSession.getSessionId());
                    
        } catch (Exception e) {
            log.error("WebSocket心跳响应处理失败: service={}, userId={}", service, userId, e);
        }
    }

    /**
     * 错误事件
     */
    @OnError
    public void onError(Throwable error) {
        String service = webSocketSession != null ? webSocketSession.getService() : "unknown";
        String userId = webSocketSession != null ? webSocketSession.getUserId() : "unknown";
        String sessionId = webSocketSession != null ? webSocketSession.getSessionId() : "unknown";
        
        try {
            // 发布错误事件
            WebSocketEvent<String> event = WebSocketEvent.onError(
                sessionId, userId, service, error.getMessage(), error
            );
            if (webSocketSession != null) {
                event.setClientIp(webSocketSession.getClientIp());
            }
            eventBus.post(event);
            
            log.error("WebSocket发生错误: service={}, userId={}, sessionId={}", service, userId, sessionId, error);
            
        } catch (Exception e) {
            log.error("WebSocket错误事件处理失败: service={}, userId={}", service, userId, e);
        }
    }

    /**
     * 发送消息
     */
    public void sendMessage(String message) {
        if (webSocketSession == null) {
            log.warn("WebSocket会话为空，无法发送消息: message={}", message);
            return;
        }
        
        try {
            webSocketSession.sendMessage(message);
            
            // 发布消息发送事件
            WebSocketEvent<String> event = WebSocketEvent.onSend(
                webSocketSession.getSessionId(), 
                webSocketSession.getUserId(), 
                webSocketSession.getService(), 
                message
            );
            event.setClientIp(webSocketSession.getClientIp());
            eventBus.post(event);
            
        } catch (IOException e) {
            log.error("WebSocket消息发送失败: service={}, userId={}, message={}", 
                    webSocketSession.getService(), webSocketSession.getUserId(), message, e);
        }
    }

    /**
     * 静态方法：根据服务和用户ID发送消息
     */
    public static boolean sendMessageToUser(String service, String userId, String message) {
        return sessionManager.sendMessage(service, userId, message);
    }

    /**
     * 静态方法：广播消息给指定服务的所有用户
     */
    public static void broadcastMessage(String service, String message) {
        sessionManager.broadcast(service, message);
    }

    /**
     * 启动心跳检测
     */
    private void startHeartbeat() {
        if (scheduledExecutorService == null) {
            log.warn("心跳检测调度器未初始化，跳过心跳检测");
            return;
        }
        
        // 发送初始心跳
        sendHeartbeat();
        
        // 调度定期心跳检测
        scheduledExecutorService.scheduleWithFixedDelay(
            this::checkHeartbeat, 
            HEARTBEAT_INTERVAL, 
            HEARTBEAT_INTERVAL, 
            TimeUnit.SECONDS
        );
    }

    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        if (webSocketSession == null || !webSocketSession.isOpen()) {
            return;
        }
        
        try {
            webSocketSession.sendPing();
            log.debug("发送WebSocket心跳: service={}, userId={}, sessionId={}", 
                    webSocketSession.getService(), webSocketSession.getUserId(), webSocketSession.getSessionId());
        } catch (IOException e) {
            log.error("WebSocket心跳发送失败: service={}, userId={}", 
                    webSocketSession.getService(), webSocketSession.getUserId(), e);
        }
    }

    /**
     * 检查心跳超时
     */
    private void checkHeartbeat() {
        if (webSocketSession == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeoutTime = nextHeartbeatTimeout.get();
        
        if (timeoutTime > 0 && currentTime >= timeoutTime) {
            // 心跳超时，关闭连接
            String service = webSocketSession.getService();
            String userId = webSocketSession.getUserId();
            
            log.warn("WebSocket心跳超时，关闭连接: service={}, userId={}, sessionId={}", 
                    service, userId, webSocketSession.getSessionId());
            
            // 发布心跳超时事件
            WebSocketEvent<String> event = WebSocketEvent.onHeartbeatTimeout(
                webSocketSession.getSessionId(), userId, service, "心跳超时"
            );
            eventBus.post(event);
            
            // 移除并关闭会话
            sessionManager.removeAndCloseSession(service, userId, webSocketSession);
        } else if (timeoutTime == 0) {
            // 初始化心跳超时时间
            nextHeartbeatTimeout.set(currentTime + HEARTBEAT_TIMEOUT * 1000L);
            sendHeartbeat();
        } else {
            // 继续发送心跳
            sendHeartbeat();
        }
    }

    /**
     * 获取WebSocket会话
     */
    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }
}