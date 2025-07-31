package com.framework.websocket.monitor;

import com.framework.websocket.health.ThreadPoolHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 线程池监控数据REST API
 * 为可视化界面提供数据接口
 *
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor/threadpool")
@CrossOrigin(origins = "*")
public class ThreadPoolMonitorController {

    @Autowired
    private ThreadPoolMonitor threadPoolMonitor;

    @Autowired
    private ThreadPoolHealthChecker healthChecker;

    @Autowired
    private ThreadPoolMetricsStore metricsStore;

    /**
     * 获取当前线程池监控数据
     */
    @GetMapping("/metrics")
    public Map<String, Object> getCurrentMetrics() {
        try {
            ThreadPoolMonitor.ThreadPoolMetrics metrics = threadPoolMonitor.manualMonitoring();
            ThreadPoolMonitor.MonitoringStatus status = threadPoolMonitor.getMonitoringStatus();

            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", System.currentTimeMillis());
            result.put("metrics", metrics);
            result.put("monitoringStatus", status);
            result.put("success", true);

            return result;

        } catch (Exception e) {
            log.error("获取线程池监控数据失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        }
    }

    /**
     * 获取健康检查结果
     */
    @GetMapping("/health")
    public Map<String, Object> getHealthStatus() {
        try {
            ThreadPoolHealthChecker.HealthCheckResult result = healthChecker.checkHealth();

            Map<String, Object> response = new HashMap<>();
            response.put("status", result.status);
            response.put("details", result.details);
            response.put("timestamp", System.currentTimeMillis());
            response.put("success", true);

            return response;

        } catch (Exception e) {
            log.error("获取健康检查结果失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return response;
        }
    }

    /**
     * 获取详细健康报告
     */
    @GetMapping("/health/report")
    public ThreadPoolHealthChecker.ThreadPoolHealthReport getDetailedHealthReport() {
        return healthChecker.getDetailedHealthReport();
    }

    /**
     * 获取历史监控数据
     */
    @GetMapping("/history")
    public Map<String, Object> getHistoryMetrics(
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "5") int intervalSeconds) {
        try {
            return metricsStore.getHistoryMetrics(minutes, intervalSeconds);
        } catch (Exception e) {
            log.error("获取历史监控数据失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        }
    }

    /**
     * 获取监控统计概览
     */
    @GetMapping("/overview")
    public Map<String, Object> getMonitoringOverview() {
        try {
            Map<String, Object> overview = new HashMap<>();

            // 当前指标
            ThreadPoolMonitor.ThreadPoolMetrics currentMetrics = threadPoolMonitor.manualMonitoring();
            ThreadPoolMonitor.MonitoringStatus monitoringStatus = threadPoolMonitor.getMonitoringStatus();
            ThreadPoolHealthChecker.HealthCheckResult healthResult = healthChecker.checkHealth();

            // 历史趋势数据（最近1小时）
            Map<String, Object> historyData = metricsStore.getHistoryMetrics(60, 10);

            HashMap<String, Object> map = new HashMap<>();
            map.put("metrics", currentMetrics);
            map.put("monitoring", monitoringStatus);
            map.put("health", healthResult);

            overview.put("current", map);

            overview.put("history", historyData);
            overview.put("timestamp", System.currentTimeMillis());
            overview.put("success", true);

            return overview;

        } catch (Exception e) {
            log.error("获取监控概览失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        }
    }

    /**
     * 触发手动监控
     */
    @PostMapping("/trigger")
    public Map<String, Object> triggerManualMonitoring() {
        try {
            ThreadPoolMonitor.ThreadPoolMetrics metrics = threadPoolMonitor.manualMonitoring();

            Map<String, Object> result = new HashMap<>();
            result.put("metrics", metrics);
            result.put("timestamp", System.currentTimeMillis());
            result.put("success", true);
            result.put("message", "手动监控已触发");

            return result;

        } catch (Exception e) {
            log.error("触发手动监控失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        }
    }
}