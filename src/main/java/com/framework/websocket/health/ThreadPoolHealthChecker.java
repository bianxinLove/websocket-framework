package com.framework.websocket.health;

import com.framework.websocket.config.WebSocketFrameworkConfig;
import com.framework.websocket.config.WebSocketFrameworkProperties;
import com.framework.websocket.monitor.ThreadPoolMonitor;
import com.framework.websocket.session.WebSocketSessionManager;
import com.framework.websocket.event.WebSocketEventBus;
import com.framework.websocket.event.WebSocketEvent;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.HashMap;

/**
 * 增强的线程池健康检查器
 * 提供线程池健康状态监控，兼容有无Spring Boot Actuator的环境
 * 
 * @author bianxin
 * @version 2.0.0
 */
@Slf4j
@Component("threadPoolHealthChecker")
public class ThreadPoolHealthChecker {

    @Autowired
    private WebSocketFrameworkProperties properties;
    
    @Autowired
    @Qualifier("webSocketExecutorService")
    private ScheduledExecutorService threadPoolExecutor;
    
    @Autowired
    private ThreadPoolMonitor threadPoolMonitor;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private WebSocketEventBus eventBus;
    
    /**
     * WebSocket连接统计
     */
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalDisconnections = new AtomicLong(0);
    
    /**
     * WebSocket消息统计
     */
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    
    /**
     * WebSocket错误统计
     */
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalHeartbeatTimeouts = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        if (properties.getFeatures().isMetrics()) {
            eventBus.register(this);
            log.info("WebSocket指标收集已启用并注册到健康检查器");
        }
    }
    
    /**
     * 监听连接建立事件
     */
    @Subscribe
    public void onConnectionOpen(WebSocketEvent<?> event) {
        if (!properties.getFeatures().isMetrics() || event == null || event.getEventType() == null) {
            return;
        }
        
        if (event.getEventType() == com.framework.websocket.event.WebSocketEventType.ON_OPEN) {
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
        if (!properties.getFeatures().isMetrics() || event == null || event.getEventType() == null) {
            return;
        }
        
        if (event.getEventType() == com.framework.websocket.event.WebSocketEventType.ON_CLOSE) {
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
        if (!properties.getFeatures().isMetrics() || event == null || event.getEventType() == null) {
            return;
        }
        
        com.framework.websocket.event.WebSocketEventType eventType = event.getEventType();
        if (eventType == com.framework.websocket.event.WebSocketEventType.ON_MESSAGE) {
            totalMessagesReceived.incrementAndGet();
        } else if (eventType == com.framework.websocket.event.WebSocketEventType.ON_SEND) {
            totalMessagesSent.incrementAndGet();
        }
    }

    /**
     * 监听错误事件
     */
    @Subscribe
    public void onError(WebSocketEvent<?> event) {
        if (!properties.getFeatures().isMetrics() || event == null || event.getEventType() == null) {
            return;
        }
        
        com.framework.websocket.event.WebSocketEventType eventType = event.getEventType();
        if (eventType == com.framework.websocket.event.WebSocketEventType.ON_ERROR) {
            totalErrors.incrementAndGet();
        } else if (eventType == com.framework.websocket.event.WebSocketEventType.ON_HEARTBEAT_TIMEOUT) {
            totalHeartbeatTimeouts.incrementAndGet();
        }
    }
    
    /**
     * 执行健康检查并返回结果Map
     */
    public HealthCheckResult checkHealth() {
        try {
            // 获取线程池监控数据
            ThreadPoolMonitor.ThreadPoolMetrics metrics = threadPoolMonitor.manualMonitoring();
            ThreadPoolMonitor.MonitoringStatus monitoringStatus = threadPoolMonitor.getMonitoringStatus();
            
            // 获取线程池统计信息
            WebSocketFrameworkConfig.TaskStatistics taskStats = getTaskStatistics();
            
            // 评估健康状态
            HealthStatus healthStatus = evaluateHealth(metrics, taskStats, monitoringStatus);
            
            // 构建健康检查结果
            HealthCheckResult result = new HealthCheckResult();
            result.status = healthStatus;
            result.details = buildHealthDetails(metrics, taskStats, monitoringStatus);
            
            return result;
            
        } catch (Exception e) {
            log.error("线程池健康检查异常", e);
            HealthCheckResult result = new HealthCheckResult();
            result.status = HealthStatus.DOWN;
            result.details = new HashMap<>();
            result.details.put("error", "健康检查异常: " + e.getMessage());
            return result;
        }
    }

    /**
     * 获取任务统计信息
     */
    private WebSocketFrameworkConfig.TaskStatistics getTaskStatistics() {
        try {
            if (threadPoolExecutor instanceof WebSocketFrameworkConfig.OptimizedScheduledThreadPoolExecutor) {
                return ((WebSocketFrameworkConfig.OptimizedScheduledThreadPoolExecutor) threadPoolExecutor).getTaskStatistics();
            }
        } catch (Exception e) {
            log.debug("无法获取任务统计信息", e);
        }
        return null;
    }

    /**
     * 评估健康状态
     */
    private HealthStatus evaluateHealth(ThreadPoolMonitor.ThreadPoolMetrics metrics, 
                                       WebSocketFrameworkConfig.TaskStatistics taskStats,
                                       ThreadPoolMonitor.MonitoringStatus monitoringStatus) {
        
        WebSocketFrameworkProperties.HealthThresholds thresholds = 
            properties.getThreadPool().getMonitoring().getHealthThresholds();
        
        // 健康评分系统
        int healthScore = 100;
        
        // 1. 检查线程池利用率
        if (metrics.poolUtilization >= thresholds.getPoolUtilizationCritical()) {
            healthScore -= 40;
        } else if (metrics.poolUtilization >= thresholds.getPoolUtilizationWarning()) {
            healthScore -= 20;
        }
        
        // 2. 检查队列利用率
        if (metrics.queueUtilization >= thresholds.getQueueUtilizationCritical()) {
            healthScore -= 30;
        } else if (metrics.queueUtilization >= thresholds.getQueueUtilizationWarning()) {
            healthScore -= 15;
        }
        
        // 3. 检查任务拒绝率
        if (taskStats != null) {
            double rejectionRate = taskStats.getRejectionRate();
            if (rejectionRate >= thresholds.getRejectionRateCritical()) {
                healthScore -= 25;
            } else if (rejectionRate >= thresholds.getRejectionRateWarning()) {
                healthScore -= 10;
            }
        }
        
        // 4. 检查监控器状态
        if (!monitoringStatus.isActive) {
            healthScore -= 15;
        }
        
        // 5. 检查当前健康状态
        if (monitoringStatus.lastHealthStatus == ThreadPoolMonitor.ThreadPoolHealthStatus.EMERGENCY) {
            healthScore -= 50;
        } else if (monitoringStatus.lastHealthStatus == ThreadPoolMonitor.ThreadPoolHealthStatus.CRITICAL) {
            healthScore -= 30;
        } else if (monitoringStatus.lastHealthStatus == ThreadPoolMonitor.ThreadPoolHealthStatus.WARNING) {
            healthScore -= 15;
        }
        
        // 根据综合评分返回健康状态
        if (healthScore >= 90) {
            return HealthStatus.UP;
        } else if (healthScore >= 70) {
            return HealthStatus.WARNING;
        } else if (healthScore >= 40) {
            return HealthStatus.CRITICAL;
        } else {
            return HealthStatus.DOWN;
        }
    }

    /**
     * 构建健康检查详细信息
     */
    private Map<String, Object> buildHealthDetails(ThreadPoolMonitor.ThreadPoolMetrics metrics,
                                                  WebSocketFrameworkConfig.TaskStatistics taskStats,
                                                  ThreadPoolMonitor.MonitoringStatus monitoringStatus) {
        
        Map<String, Object> details = new HashMap<>();
        
        // 线程池基本信息
        details.put("threadPool.coreSize", metrics.corePoolSize);
        details.put("threadPool.maximumSize", metrics.maximumPoolSize);
        details.put("threadPool.activeCount", metrics.activeCount);
        details.put("threadPool.poolUtilization", String.format("%.1f%%", metrics.poolUtilization * 100));
        
        // 队列信息
        details.put("queue.size", metrics.queueSize);
        details.put("queue.capacity", properties.getThreadPool().getQueueCapacity());
        details.put("queue.utilization", String.format("%.1f%%", metrics.queueUtilization * 100));
        
        // 任务统计
        details.put("tasks.completed", metrics.completedTaskCount);
        details.put("tasks.total", metrics.taskCount);
        details.put("tasks.throughput", String.format("%.2f tasks/s", metrics.throughput));
        
        // 任务拒绝统计
        if (taskStats != null) {
            details.put("tasks.rejected", taskStats.rejectedTasks);
            details.put("tasks.rejectionRate", String.format("%.3f%%", taskStats.getRejectionRate() * 100));
            details.put("tasks.completionRate", String.format("%.1f%%", taskStats.getCompletionRate() * 100));
        }
        
        // JVM线程信息
        details.put("jvm.totalThreads", metrics.totalThreadCount);
        details.put("jvm.peakThreads", metrics.peakThreadCount);
        
        // 监控器状态
        details.put("monitor.active", monitoringStatus.isActive);
        details.put("monitor.currentInterval", monitoringStatus.currentInterval + "s");
        details.put("monitor.samplingRate", "1:" + monitoringStatus.samplingRate);
        details.put("monitor.healthStatus", monitoringStatus.lastHealthStatus.toString());
        details.put("monitor.executions", monitoringStatus.monitoringExecutions);
        details.put("monitor.avgCostMs", String.format("%.3f ms", monitoringStatus.avgMonitoringCostMs));
        
        // 配置信息
        WebSocketFrameworkProperties.ThreadPool config = properties.getThreadPool();
        details.put("config.taskTimeout", config.getTaskTimeout() + "s");
        details.put("config.keepAlive", config.getKeepAlive() + "s");
        details.put("config.queueWarningThreshold", config.getQueueWarningThreshold());
        details.put("config.queueDangerThreshold", config.getQueueDangerThreshold());
        
        // 时间戳
        details.put("checkTime", System.currentTimeMillis());
        
        return details;
    }

    /**
     * 获取详细的健康报告
     */
    public ThreadPoolHealthReport getDetailedHealthReport() {
        ThreadPoolHealthReport report = new ThreadPoolHealthReport();
        
        try {
            ThreadPoolMonitor.ThreadPoolMetrics metrics = threadPoolMonitor.manualMonitoring();
            ThreadPoolMonitor.MonitoringStatus monitoringStatus = threadPoolMonitor.getMonitoringStatus();
            
            report.timestamp = System.currentTimeMillis();
            report.healthStatus = monitoringStatus.lastHealthStatus;
            report.metrics = metrics;
            report.monitoringStatus = monitoringStatus;
            report.taskStatistics = getTaskStatistics();
            
            // 生成建议
            report.recommendations = generateRecommendations(metrics, report.taskStatistics, monitoringStatus);
            
        } catch (Exception e) {
            log.error("生成健康报告失败", e);
            report.error = e.getMessage();
        }
        
        return report;
    }

    /**
     * 生成优化建议
     */
    private String[] generateRecommendations(ThreadPoolMonitor.ThreadPoolMetrics metrics,
                                           WebSocketFrameworkConfig.TaskStatistics taskStats,
                                           ThreadPoolMonitor.MonitoringStatus monitoringStatus) {
        
        java.util.List<String> recommendations = new java.util.ArrayList<>();
        
        // 线程池大小建议
        if (metrics.poolUtilization > 0.8) {
            recommendations.add("考虑增加线程池核心线程数，当前利用率: " + String.format("%.1f%%", metrics.poolUtilization * 100));
        }
        
        // 队列大小建议
        if (metrics.queueUtilization > 0.7) {
            recommendations.add("考虑增加队列容量或优化任务处理速度，当前队列利用率: " + String.format("%.1f%%", metrics.queueUtilization * 100));
        }
        
        // 任务拒绝建议
        if (taskStats != null && taskStats.getRejectionRate() > 0.01) {
            recommendations.add("任务拒绝率较高(" + String.format("%.3f%%", taskStats.getRejectionRate() * 100) + ")，建议检查系统负载");
        }
        
        // 吞吐量建议
        if (metrics.throughput < 1.0) {
            recommendations.add("吞吐量较低(" + String.format("%.2f tasks/s", metrics.throughput) + ")，建议检查任务执行效率");
        }
        
        // 监控器建议
        if (monitoringStatus.avgMonitoringCostMs > 10.0) {
            recommendations.add("监控开销较高(" + String.format("%.2f ms", monitoringStatus.avgMonitoringCostMs) + ")，已自动调整采样率");
        }
        
        return recommendations.toArray(new String[0]);
    }
    
    /**
     * 获取当前WebSocket指标快照
     */
    public WebSocketMetricsSnapshot getWebSocketMetricsSnapshot() {
        WebSocketMetricsSnapshot snapshot = new WebSocketMetricsSnapshot();
        snapshot.currentConnections = sessionManager.getTotalOnlineCount();
        snapshot.totalConnections = totalConnections.get();
        snapshot.totalDisconnections = totalDisconnections.get();
        snapshot.totalMessagesReceived = totalMessagesReceived.get();
        snapshot.totalMessagesSent = totalMessagesSent.get();
        snapshot.totalErrors = totalErrors.get();
        snapshot.totalHeartbeatTimeouts = totalHeartbeatTimeouts.get();
        return snapshot;
    }

    /**
     * WebSocket指标快照数据类
     */
    public static class WebSocketMetricsSnapshot {
        public int currentConnections;
        public long totalConnections;
        public long totalDisconnections;
        public long totalMessagesReceived;
        public long totalMessagesSent;
        public long totalErrors;
        public long totalHeartbeatTimeouts;
    }

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        UP("正常"),
        WARNING("警告"),
        CRITICAL("严重"),
        DOWN("异常");
        
        private final String description;
        
        HealthStatus(String description) {
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        public HealthStatus status;
        public Map<String, Object> details;
    }

    /**
     * 健康报告数据结构
     */
    public static class ThreadPoolHealthReport {
        public long timestamp;
        public ThreadPoolMonitor.ThreadPoolHealthStatus healthStatus;
        public ThreadPoolMonitor.ThreadPoolMetrics metrics;
        public ThreadPoolMonitor.MonitoringStatus monitoringStatus;
        public WebSocketFrameworkConfig.TaskStatistics taskStatistics;
        public String[] recommendations;
        public String error;
    }
}