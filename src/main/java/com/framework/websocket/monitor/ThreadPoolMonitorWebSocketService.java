package com.framework.websocket.monitor;

import com.framework.websocket.annotation.WebSocketService;
import com.framework.websocket.handler.WebSocketMessageHandler;
import com.framework.websocket.event.WebSocketEvent;
import com.framework.websocket.session.WebSocketSessionManager;
import com.framework.websocket.health.ThreadPoolHealthChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 线程池监控WebSocket服务
 * 为监控页面提供实时数据推送
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
@WebSocketService("monitor")
public class ThreadPoolMonitorWebSocketService implements WebSocketMessageHandler<String> {

    @Autowired
    private ThreadPoolMonitor threadPoolMonitor;
    
    @Autowired
    private ThreadPoolHealthChecker healthChecker;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object handleEvent(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        
        switch (event.getEventType()) {
            case ON_OPEN:
                log.info("监控客户端连接: {}", userId);
                sendWelcomeMessage(userId);
                sendCurrentMetrics(userId);
                break;
                
            case ON_MESSAGE:
                handleMonitorCommand(userId, event.getData());
                break;
                
            case ON_CLOSE:
                log.info("监控客户端断开: {}", userId);
                break;
                
            case ON_ERROR:
                log.error("监控客户端错误: {}, error: {}", userId, event.getErrorMessage());
                break;
                
            default:
                // 其他事件不处理
                break;
        }
        return null;
    }

    /**
     * 发送欢迎消息
     */
    private void sendWelcomeMessage(String userId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "welcome");
            message.put("message", "欢迎连接线程池监控中心");
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            sessionManager.sendMessage("monitor", userId, jsonMessage);
            
        } catch (Exception e) {
            log.error("发送欢迎消息失败: {}", e.getMessage());
        }
    }

    /**
     * 发送当前监控数据
     */
    private void sendCurrentMetrics(String userId) {
        try {
            ThreadPoolMonitor.ThreadPoolMetrics metrics = threadPoolMonitor.manualMonitoring();
            ThreadPoolMonitor.MonitoringStatus status = threadPoolMonitor.getMonitoringStatus();
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("metrics", metrics);
            payload.put("monitoringStatus", status);
            payload.put("timestamp", System.currentTimeMillis());
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "threadpool_metrics");
            message.put("payload", payload);
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            sessionManager.sendMessage("monitor", userId, jsonMessage);
            
        } catch (Exception e) {
            log.error("发送监控数据失败: {}", e.getMessage());
        }
    }

    /**
     * 处理监控命令
     */
    private void handleMonitorCommand(String userId, String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> command = objectMapper.readValue(message, Map.class);
            String action = (String) command.get("action");
            
            switch (action) {
                case "refresh":
                    sendCurrentMetrics(userId);
                    break;
                    
                case "health_check":
                    sendHealthCheckResult(userId);
                    break;
                    
                case "detailed_report":
                    sendDetailedReport(userId);
                    break;
                    
                default:
                    sendErrorMessage(userId, "未知命令: " + action);
                    break;
            }
            
        } catch (Exception e) {
            log.error("处理监控命令失败: {}", e.getMessage());
            sendErrorMessage(userId, "命令处理失败: " + e.getMessage());
        }
    }

    /**
     * 发送健康检查结果
     */
    private void sendHealthCheckResult(String userId) {
        try {
            ThreadPoolHealthChecker.HealthCheckResult result = healthChecker.checkHealth();
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "health_check");
            message.put("payload", result);
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            sessionManager.sendMessage("monitor", userId, jsonMessage);
            
        } catch (Exception e) {
            log.error("发送健康检查结果失败: {}", e.getMessage());
        }
    }

    /**
     * 发送详细报告
     */
    private void sendDetailedReport(String userId) {
        try {
            ThreadPoolHealthChecker.ThreadPoolHealthReport report = healthChecker.getDetailedHealthReport();
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "detailed_report");
            message.put("payload", report);
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            sessionManager.sendMessage("monitor", userId, jsonMessage);
            
        } catch (Exception e) {
            log.error("发送详细报告失败: {}", e.getMessage());
        }
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(String userId, String error) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "error");
            message.put("message", error);
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            sessionManager.sendMessage("monitor", userId, jsonMessage);
            
        } catch (Exception e) {
            log.error("发送错误消息失败: {}", e.getMessage());
        }
    }

    /**
     * 定期广播监控数据（每30秒）
     */
    @Scheduled(fixedRate = 30000)
    public void broadcastMetrics() {
        try {
            // 检查是否有活跃的监控客户端
            if (sessionManager.getOnlineCount("monitor") == 0) {
                return;
            }
            
            ThreadPoolMonitor.ThreadPoolMetrics metrics = threadPoolMonitor.manualMonitoring();
            ThreadPoolMonitor.MonitoringStatus status = threadPoolMonitor.getMonitoringStatus();
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("metrics", metrics);
            payload.put("monitoringStatus", status);
            payload.put("timestamp", System.currentTimeMillis());
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "threadpool_metrics");
            message.put("payload", payload);
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            // 广播给所有监控客户端
            sessionManager.broadcast("monitor", jsonMessage);
            
            log.debug("广播监控数据完成，活跃客户端数: {}", 
                sessionManager.getOnlineCount("monitor"));
                
        } catch (Exception e) {
            log.warn("广播监控数据失败: {}", e.getMessage());
        }
    }

    /**
     * 健康状态变化时发送警报
     */
    public void sendHealthAlert(ThreadPoolMonitor.ThreadPoolHealthStatus oldStatus, 
                               ThreadPoolMonitor.ThreadPoolHealthStatus newStatus) {
        try {
            if (sessionManager.getOnlineCount("monitor") == 0) {
                return;
            }
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("oldStatus", oldStatus.toString());
            payload.put("newStatus", newStatus.toString());
            payload.put("message", String.format("健康状态从 %s 变更为 %s", oldStatus, newStatus));
            payload.put("level", getAlertLevel(newStatus));
            payload.put("timestamp", System.currentTimeMillis());
            
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "health_alert");
            alert.put("payload", payload);
            
            String jsonMessage = objectMapper.writeValueAsString(alert);
            sessionManager.broadcast("monitor", jsonMessage);
            
            log.info("发送健康状态警报: {} -> {}", oldStatus, newStatus);
            
        } catch (Exception e) {
            log.error("发送健康警报失败: {}", e.getMessage());
        }
    }

    /**
     * 根据健康状态获取警报级别
     */
    private String getAlertLevel(ThreadPoolMonitor.ThreadPoolHealthStatus status) {
        switch (status) {
            case HEALTHY:
                return "info";
            case WARNING:
                return "warning";
            case CRITICAL:
                return "error";
            case EMERGENCY:
                return "critical";
            default:
                return "info";
        }
    }

    /**
     * 发送系统统计信息
     */
    @Scheduled(fixedRate = 300000) // 每5分钟发送一次统计信息
    public void broadcastSystemStats() {
        try {
            if (sessionManager.getOnlineCount("monitor") == 0) {
                return;
            }
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("jvmMemory", getJvmMemoryInfo());
            payload.put("systemLoad", getSystemLoadInfo());
            payload.put("monitoringStats", threadPoolMonitor.getMonitoringStatus());
            payload.put("timestamp", System.currentTimeMillis());
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("type", "system_stats");
            stats.put("payload", payload);
            
            String jsonMessage = objectMapper.writeValueAsString(stats);
            sessionManager.broadcast("monitor", jsonMessage);
            
        } catch (Exception e) {
            log.warn("广播系统统计信息失败: {}", e.getMessage());
        }
    }

    /**
     * 获取JVM内存信息
     */
    private Map<String, Object> getJvmMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new HashMap<>();
        memory.put("totalMemory", runtime.totalMemory());
        memory.put("freeMemory", runtime.freeMemory());
        memory.put("maxMemory", runtime.maxMemory());
        memory.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        return memory;
    }

    /**
     * 获取系统负载信息
     */
    private Map<String, Object> getSystemLoadInfo() {
        Map<String, Object> load = new HashMap<>();
        try {
            java.lang.management.OperatingSystemMXBean osBean = 
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            
            load.put("availableProcessors", osBean.getAvailableProcessors());
            load.put("systemLoadAverage", osBean.getSystemLoadAverage());
            
            // 安全地尝试获取CPU使用率
            try {
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunOsBean = 
                        (com.sun.management.OperatingSystemMXBean) osBean;
                    double processCpuLoad = sunOsBean.getProcessCpuLoad();
                    if (processCpuLoad >= 0) {
                        load.put("processCpuLoad", processCpuLoad);
                    }
                    double systemCpuLoad = sunOsBean.getSystemCpuLoad();
                    if (systemCpuLoad >= 0) {
                        load.put("systemCpuLoad", systemCpuLoad);
                    }
                }
            } catch (Exception e) {
                log.debug("无法获取CPU使用率，使用替代方案: {}", e.getMessage());
                // 使用系统负载平均值作为替代
                double systemLoadAverage = osBean.getSystemLoadAverage();
                if (systemLoadAverage > 0) {
                    double estimatedCpuLoad = Math.min(1.0, 
                        systemLoadAverage / osBean.getAvailableProcessors());
                    load.put("estimatedCpuLoad", estimatedCpuLoad);
                }
            }
            
        } catch (Exception e) {
            log.debug("获取系统负载信息失败: {}", e.getMessage());
            // 提供基本信息
            load.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            load.put("error", "无法获取详细系统信息");
        }
        return load;
    }
}