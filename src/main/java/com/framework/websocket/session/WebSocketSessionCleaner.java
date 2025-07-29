package com.framework.websocket.session;

import com.framework.websocket.config.WebSocketFrameworkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的WebSocket会话清理器
 * 定期清理断开连接和超时的会话，支持内存压力检测
 * 
 * @author bianxin
 * @version 2.0.0
 */
@Slf4j
@Component
public class WebSocketSessionCleaner {

    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private WebSocketFrameworkProperties properties;
    
    // 内存监控相关
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final AtomicLong totalCleanedSessions = new AtomicLong(0);
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());
    
    // 内存压力阈值（80%）
    private static final double MEMORY_PRESSURE_THRESHOLD = 0.8;

    /**
     * 定期清理无效会话（标准清理）
     */
    @Scheduled(fixedRateString = "#{${websocket.framework.session.cleanup-interval:60} * 1000}")
    public void cleanInactiveSessions() {
        if (!properties.getFeatures().isHealthCheck()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        boolean isMemoryPressure = checkMemoryPressure();
        
        log.debug("开始清理无效WebSocket会话，内存压力: {}", isMemoryPressure);
        
        CleanupResult result = performCleanup(isMemoryPressure);
        
        // 更新统计信息
        totalCleanedSessions.addAndGet(result.cleanedCount);
        lastCleanupTime.set(startTime);
        
        // 记录清理结果
        long duration = System.currentTimeMillis() - startTime;
        if (result.cleanedCount > 0 || isMemoryPressure) {
            log.info("会话清理完成: 清理会话={}, 强制清理Map={}, 耗时={}ms, 内存状态={}", 
                result.cleanedCount, result.mapCleanedCount, duration, 
                isMemoryPressure ? "压力大" : "正常");
        }
        
        // 在内存压力下额外执行垃圾回收建议
        if (isMemoryPressure && result.cleanedCount > 0) {
            log.info("内存压力较大，建议执行垃圾回收");
            System.gc(); // 建议性GC，实际执行由JVM决定
        }
    }
    
    /**
     * 紧急内存清理（在检测到严重内存压力时触发）
     */
    @Scheduled(fixedRate = 30000) // 每30秒检查一次
    public void emergencyMemoryCleanup() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        // 内存使用率超过90%时触发紧急清理
        if (memoryUsageRatio > 0.9) {
            log.warn("检测到严重内存压力({}%)，执行紧急清理", String.format("%.1f", memoryUsageRatio * 100));
            
            CleanupResult result = performAggressiveCleanup();
            
            log.warn("紧急清理完成: 清理会话={}, 强制清理Map={}, 当前内存使用率={}%", 
                result.cleanedCount, result.mapCleanedCount, 
                String.format("%.1f", getCurrentMemoryUsage() * 100));
        }
    }

    /**
     * 执行标准清理
     */
    private CleanupResult performCleanup(boolean isMemoryPressure) {
        CleanupResult result = new CleanupResult();
        List<SessionInfo> sessionsToClean = new ArrayList<>();
        
        // 收集需要清理的会话
        Map<String, Map<String, WebSocketSession>> allSessions = sessionManager.getAllSessions();
        for (Map.Entry<String, Map<String, WebSocketSession>> serviceEntry : allSessions.entrySet()) {
            String service = serviceEntry.getKey();
            for (Map.Entry<String, WebSocketSession> userEntry : serviceEntry.getValue().entrySet()) {
                String userId = userEntry.getKey();
                WebSocketSession session = userEntry.getValue();
                
                if (shouldCleanSession(session, isMemoryPressure)) {
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
                result.cleanedCount++;
            } catch (Exception e) {
                log.error("清理会话失败: service={}, userId={}", 
                    sessionInfo.service, sessionInfo.userId, e);
            }
        }
        
        // 强制清理空的Map（在内存压力下或定期执行）
        if (isMemoryPressure || shouldForceMapCleanup()) {
            sessionManager.forceCleanup();
            result.mapCleanedCount = 1;
        }
        
        return result;
    }
    
    /**
     * 执行激进清理（紧急情况）
     */
    private CleanupResult performAggressiveCleanup() {
        CleanupResult result = new CleanupResult();
        List<SessionInfo> sessionsToClean = new ArrayList<>();
        
        // 更激进的清理策略
        Map<String, Map<String, WebSocketSession>> allSessions = sessionManager.getAllSessions();
        for (Map.Entry<String, Map<String, WebSocketSession>> serviceEntry : allSessions.entrySet()) {
            String service = serviceEntry.getKey();
            for (Map.Entry<String, WebSocketSession> userEntry : serviceEntry.getValue().entrySet()) {
                String userId = userEntry.getKey();
                WebSocketSession session = userEntry.getValue();
                
                // 激进模式：清理所有非活跃会话（降低空闲时间阈值）
                if (shouldCleanSessionAggressively(session)) {
                    sessionsToClean.add(new SessionInfo(service, userId, session));
                }
            }
        }
        
        // 批量清理
        for (SessionInfo sessionInfo : sessionsToClean) {
            try {
                sessionManager.removeAndCloseSession(
                    sessionInfo.service, 
                    sessionInfo.userId, 
                    sessionInfo.session
                );
                result.cleanedCount++;
            } catch (Exception e) {
                log.debug("紧急清理会话失败: service={}, userId={}", 
                    sessionInfo.service, sessionInfo.userId, e);
            }
        }
        
        // 强制清理所有空Map
        sessionManager.forceCleanup();
        result.mapCleanedCount = 1;
        
        return result;
    }
    
    /**
     * 判断会话是否需要清理
     */
    private boolean shouldCleanSession(WebSocketSession session, boolean isMemoryPressure) {
        // 检查连接状态
        if (!session.isOpen()) {
            return true;
        }
        
        // 检查空闲时间
        long maxIdleTime = properties.getSession().getMaxIdleTime() * 1000L;
        
        // 在内存压力下缩短空闲时间阈值
        if (isMemoryPressure) {
            maxIdleTime = maxIdleTime / 2; // 减半
        }
        
        long lastHeartbeat = session.getLastHeartbeatTime().get();
        long currentTime = System.currentTimeMillis();
        
        return (currentTime - lastHeartbeat) > maxIdleTime;
    }
    
    /**
     * 激进模式下判断会话是否需要清理
     */
    private boolean shouldCleanSessionAggressively(WebSocketSession session) {
        if (!session.isOpen()) {
            return true;
        }
        
        // 激进模式：空闲时间阈值更短（1/4的配置值）
        long maxIdleTime = properties.getSession().getMaxIdleTime() * 1000L / 4;
        long lastHeartbeat = session.getLastHeartbeatTime().get();
        long currentTime = System.currentTimeMillis();
        
        return (currentTime - lastHeartbeat) > maxIdleTime;
    }
    
    /**
     * 检查内存压力
     */
    private boolean checkMemoryPressure() {
        return getCurrentMemoryUsage() > MEMORY_PRESSURE_THRESHOLD;
    }
    
    /**
     * 获取当前内存使用率
     */
    private double getCurrentMemoryUsage() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }
    
    /**
     * 判断是否应该执行强制Map清理
     */
    private boolean shouldForceMapCleanup() {
        // 每5分钟强制清理一次
        return (System.currentTimeMillis() - lastCleanupTime.get()) > 300000;
    }
    
    /**
     * 获取清理统计信息
     */
    public String getCleanupStats() {
        double memoryUsage = getCurrentMemoryUsage();
        return String.format("总清理会话数: %d, 上次清理时间: %d分钟前, 当前内存使用率: %.1f%%, 会话统计: %s", 
            totalCleanedSessions.get(),
            (System.currentTimeMillis() - lastCleanupTime.get()) / 60000,
            memoryUsage * 100,
            sessionManager.getMemoryStats());
    }
    
    /**
     * 定期清理Redis中过期的心跳缓存（每5分钟执行一次）
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void cleanupExpiredHeartbeatCache() {
        if (!properties.getFeatures().isHealthCheck()) {
            return;
        }
        
        try {
            sessionManager.cleanupExpiredHeartbeats();
        } catch (Exception e) {
            log.error("清理过期心跳缓存失败", e);
        }
    }
    
    /**
     * 手动触发清理
     */
    public CleanupResult manualCleanup(boolean aggressive) {
        log.info("手动触发会话清理，激进模式: {}", aggressive);
        return aggressive ? performAggressiveCleanup() : performCleanup(true);
    }
    
    /**
     * 清理结果
     */
    public static class CleanupResult {
        public int cleanedCount = 0;
        public int mapCleanedCount = 0;
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