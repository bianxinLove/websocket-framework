package com.framework.websocket.metrics;

import com.framework.websocket.config.WebSocketFrameworkProperties;
import com.framework.websocket.event.WebSocketEvent;
import com.framework.websocket.event.WebSocketEventBus;
import com.framework.websocket.session.WebSocketSessionManager;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket指标收集器
 * 收集连接数、消息数等指标信息
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class WebSocketMetricsCollector {

    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private WebSocketFrameworkProperties properties;
    
    @Autowired
    private WebSocketEventBus eventBus;

    /**
     * 连接统计
     */
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalDisconnections = new AtomicLong(0);
    
    /**
     * 消息统计
     */
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    
    /**
     * 错误统计
     */
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalHeartbeatTimeouts = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        if (properties.getFeatures().isMetrics()) {
            eventBus.register(this);
            log.info("WebSocket指标收集器已启用并注册到事件总线");
        }
    }

    /**
     * 监听连接建立事件
     */
    @Subscribe
    public void onConnectionOpen(WebSocketEvent<?> event) {
        if (!properties.getFeatures().isMetrics()) {
            return;
        }
        
        if (event.getEventType().name().equals("ON_OPEN")) {
            totalConnections.incrementAndGet();
            log.debug("连接建立指标更新: service={}, userId={}, 总连接数={}", 
                event.getService(), event.getUserId(), totalConnections.get());
        }
    }

    /**
     * 监听连接关闭事件
     */
    @Subscribe
    public void onConnectionClose(WebSocketEvent<?> event) {
        if (!properties.getFeatures().isMetrics()) {
            return;
        }
        
        if (event.getEventType().name().equals("ON_CLOSE")) {
            totalDisconnections.incrementAndGet();
            log.debug("连接关闭指标更新: service={}, userId={}, 总断开数={}", 
                event.getService(), event.getUserId(), totalDisconnections.get());
        }
    }

    /**
     * 监听消息事件
     */
    @Subscribe
    public void onMessage(WebSocketEvent<?> event) {
        if (!properties.getFeatures().isMetrics()) {
            return;
        }
        
        String eventType = event.getEventType().name();
        if ("ON_MESSAGE".equals(eventType)) {
            totalMessagesReceived.incrementAndGet();
        } else if ("ON_SEND".equals(eventType)) {
            totalMessagesSent.incrementAndGet();
        }
    }

    /**
     * 监听错误事件
     */
    @Subscribe
    public void onError(WebSocketEvent<?> event) {
        if (!properties.getFeatures().isMetrics()) {
            return;
        }
        
        String eventType = event.getEventType().name();
        if ("ON_ERROR".equals(eventType)) {
            totalErrors.incrementAndGet();
        } else if ("ON_HEARTBEAT_TIMEOUT".equals(eventType)) {
            totalHeartbeatTimeouts.incrementAndGet();
        }
    }

    /**
     * 获取当前指标快照
     */
    public MetricsSnapshot getMetricsSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setCurrentConnections(sessionManager.getTotalOnlineCount());
        snapshot.setTotalConnections(totalConnections.get());
        snapshot.setTotalDisconnections(totalDisconnections.get());
        snapshot.setTotalMessagesReceived(totalMessagesReceived.get());
        snapshot.setTotalMessagesSent(totalMessagesSent.get());
        snapshot.setTotalErrors(totalErrors.get());
        snapshot.setTotalHeartbeatTimeouts(totalHeartbeatTimeouts.get());
        return snapshot;
    }

    /**
     * 指标快照数据类
     */
    @Data
    public static class MetricsSnapshot {
        private int currentConnections;
        private long totalConnections;
        private long totalDisconnections;
        private long totalMessagesReceived;
        private long totalMessagesSent;
        private long totalErrors;
        private long totalHeartbeatTimeouts;
    }
}