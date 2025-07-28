package com.framework.websocket.session;

import com.framework.websocket.config.WebSocketFrameworkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebSocket会话清理器
 * 定期清理断开连接和超时的会话
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class WebSocketSessionCleaner {

    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private WebSocketFrameworkProperties properties;

    /**
     * 定期清理无效会话
     */
    @Scheduled(fixedRateString = "#{${websocket.framework.session.cleanup-interval:60} * 1000}")
    public void cleanInactiveSessions() {
        if (!properties.getFeatures().isHealthCheck()) {
            return;
        }
        
        log.debug("开始清理无效WebSocket会话");
        
        int cleanedCount = 0;
        List<SessionInfo> sessionsToClean = new ArrayList<>();
        
        // 收集需要清理的会话
        Map<String, Map<String, WebSocketSession>> allSessions = sessionManager.getAllSessions();
        for (Map.Entry<String, Map<String, WebSocketSession>> serviceEntry : allSessions.entrySet()) {
            String service = serviceEntry.getKey();
            for (Map.Entry<String, WebSocketSession> userEntry : serviceEntry.getValue().entrySet()) {
                String userId = userEntry.getKey();
                WebSocketSession session = userEntry.getValue();
                
                if (shouldCleanSession(session)) {
                    sessionsToClean.add(new SessionInfo(service, userId, session));
                }
            }
        }
        
        // 清理会话
        for (SessionInfo sessionInfo : sessionsToClean) {
            try {
                sessionManager.removeAndCloseSession(
                    sessionInfo.service, 
                    sessionInfo.userId, 
                    sessionInfo.session
                );
                cleanedCount++;
            } catch (Exception e) {
                log.error("清理会话失败: service={}, userId={}", 
                    sessionInfo.service, sessionInfo.userId, e);
            }
        }
        
        if (cleanedCount > 0) {
            log.info("会话清理完成，清理了{}个无效会话", cleanedCount);
        }
    }
    
    /**
     * 判断会话是否需要清理
     */
    private boolean shouldCleanSession(WebSocketSession session) {
        // 检查连接状态
        if (!session.isOpen()) {
            return true;
        }
        
        // 检查空闲时间
        long maxIdleTime = properties.getSession().getMaxIdleTime() * 1000L;
        long lastHeartbeat = session.getLastHeartbeatTime().get();
        long currentTime = System.currentTimeMillis();
        
        return (currentTime - lastHeartbeat) > maxIdleTime;
    }
    
    /**
     * 会话信息包装类
     */
    private static class SessionInfo {
        final String service;
        final String userId;
        final WebSocketSession session;
        
        SessionInfo(String service, String userId, WebSocketSession session) {
            this.service = service;
            this.userId = userId;
            this.session = session;
        }
    }
}