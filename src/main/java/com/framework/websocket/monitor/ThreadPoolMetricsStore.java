package com.framework.websocket.monitor;

import com.framework.websocket.health.ThreadPoolHealthChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 线程池监控数据存储服务
 * 使用Redis存储历史监控数据，支持可视化查询
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "websocket.framework.features.redis-enabled", havingValue = "true", matchIfMissing = true)
public class ThreadPoolMetricsStore {

    private static final String METRICS_KEY_PREFIX = "websocket:threadpool:metrics:";
    private static final String HEALTH_KEY_PREFIX = "websocket:threadpool:health:";
    private static final int MAX_HISTORY_MINUTES = 1440; // 保留24小时数据
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ThreadPoolMonitor threadPoolMonitor;
    
    @Autowired
    private ThreadPoolHealthChecker healthChecker;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initialize() {
        log.info("线程池监控数据存储服务已启动，数据保留时间: {}小时", MAX_HISTORY_MINUTES / 60);
    }

    /**
     * 定期收集和存储监控数据
     */
    @Scheduled(fixedRate = 30000) // 每30秒收集一次
    public void collectAndStoreMetrics() {
        try {
            long timestamp = System.currentTimeMillis();
            
            // 收集监控数据
            ThreadPoolMonitor.ThreadPoolMetrics metrics = threadPoolMonitor.manualMonitoring();
            ThreadPoolMonitor.MonitoringStatus monitoringStatus = threadPoolMonitor.getMonitoringStatus();
            
            // 构建存储数据
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("metrics", metrics);
            dataPoint.put("monitoringStatus", monitoringStatus);
            
            // 存储到Redis
            String metricsKey = METRICS_KEY_PREFIX + timestamp;
            redisTemplate.opsForValue().set(metricsKey, dataPoint, MAX_HISTORY_MINUTES, TimeUnit.MINUTES);
            
            // 每5分钟收集一次健康检查数据
            if (timestamp % 300000 < 30000) { // 5分钟内的前30秒
                collectAndStoreHealthData(timestamp);
            }
            
        } catch (Exception e) {
            log.warn("收集监控数据失败", e);
        }
    }

    /**
     * 收集和存储健康检查数据
     */
    private void collectAndStoreHealthData(long timestamp) {
        try {
            ThreadPoolHealthChecker.HealthCheckResult healthResult = healthChecker.checkHealth();
            
            Map<String, Object> healthData = new HashMap<>();
            healthData.put("timestamp", timestamp);
            healthData.put("status", healthResult.status);
            healthData.put("details", healthResult.details);
            
            String healthKey = HEALTH_KEY_PREFIX + timestamp;
            redisTemplate.opsForValue().set(healthKey, healthData, MAX_HISTORY_MINUTES, TimeUnit.MINUTES);
            
        } catch (Exception e) {
            log.warn("收集健康检查数据失败", e);
        }
    }

    /**
     * 获取历史监控数据
     */
    public Map<String, Object> getHistoryMetrics(int minutes, int intervalSeconds) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (minutes * 60 * 1000L);
            long interval = intervalSeconds * 1000L;
            
            List<Map<String, Object>> timeSeriesData = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            
            // 按时间间隔采样数据
            for (long time = startTime; time <= endTime; time += interval) {
                String metricsKey = findNearestMetricsKey(time);
                if (metricsKey != null) {
                    Object data = redisTemplate.opsForValue().get(metricsKey);
                    if (data instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dataPoint = (Map<String, Object>) data;
                        timeSeriesData.add(dataPoint);
                        labels.add(new Date((Long) dataPoint.get("timestamp")).toString());
                    }
                }
            }
            
            // 处理时间序列数据
            Map<String, List<Object>> chartData = processTimeSeriesData(timeSeriesData);
            
            result.put("success", true);
            result.put("data", chartData);
            result.put("labels", labels);
            result.put("startTime", startTime);
            result.put("endTime", endTime);
            result.put("dataPoints", timeSeriesData.size());
            
        } catch (Exception e) {
            log.error("获取历史监控数据失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 查找最接近指定时间的监控数据键
     */
    private String findNearestMetricsKey(long targetTime) {
        // 在目标时间前后30秒范围内查找
        for (int offset = 0; offset <= 30000; offset += 5000) {
            String key1 = METRICS_KEY_PREFIX + (targetTime + offset);
            String key2 = METRICS_KEY_PREFIX + (targetTime - offset);
            
            if (redisTemplate.hasKey(key1)) {
                return key1;
            }
            if (offset > 0 && redisTemplate.hasKey(key2)) {
                return key2;
            }
        }
        return null;
    }

    /**
     * 处理时间序列数据，提取图表所需的数据格式
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<Object>> processTimeSeriesData(List<Map<String, Object>> timeSeriesData) {
        Map<String, List<Object>> chartData = new HashMap<>();
        
        // 初始化数据序列
        chartData.put("poolUtilization", new ArrayList<>());
        chartData.put("queueUtilization", new ArrayList<>());
        chartData.put("activeCount", new ArrayList<>());
        chartData.put("queueSize", new ArrayList<>());
        chartData.put("throughput", new ArrayList<>());
        chartData.put("completedTasks", new ArrayList<>());
        chartData.put("healthScore", new ArrayList<>());
        
        for (Map<String, Object> dataPoint : timeSeriesData) {
            if (dataPoint.get("metrics") instanceof Map) {
                Map<String, Object> metrics = (Map<String, Object>) dataPoint.get("metrics");
                
                chartData.get("poolUtilization").add(getDoubleValue(metrics, "poolUtilization") * 100);
                chartData.get("queueUtilization").add(getDoubleValue(metrics, "queueUtilization") * 100);
                chartData.get("activeCount").add(getIntValue(metrics, "activeCount"));
                chartData.get("queueSize").add(getIntValue(metrics, "queueSize"));
                chartData.get("throughput").add(getDoubleValue(metrics, "throughput"));
                chartData.get("completedTasks").add(getLongValue(metrics, "completedTaskCount"));
                
                // 计算健康评分
                double healthScore = calculateHealthScore(metrics);
                chartData.get("healthScore").add(healthScore);
            }
        }
        
        return chartData;
    }

    /**
     * 计算健康评分（0-100）
     */
    @SuppressWarnings("unchecked")
    private double calculateHealthScore(Map<String, Object> metrics) {
        double score = 100.0;
        
        double poolUtilization = getDoubleValue(metrics, "poolUtilization");
        double queueUtilization = getDoubleValue(metrics, "queueUtilization");
        
        if (poolUtilization > 0.9) {
            score -= 30;
        } else if (poolUtilization > 0.7) {
            score -= 15;
        }
        
        if (queueUtilization > 0.8) {
            score -= 25;
        } else if (queueUtilization > 0.5) {
            score -= 10;
        }
        
        return Math.max(0, score);
    }

    /**
     * 获取实时统计数据
     */
    public Map<String, Object> getRealTimeStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 获取最近一小时的数据
            Map<String, Object> hourlyData = getHistoryMetrics(60, 60);
            
            if (hourlyData.get("success").equals(true)) {
                @SuppressWarnings("unchecked")
                Map<String, List<Object>> chartData = (Map<String, List<Object>>) hourlyData.get("data");
                
                // 计算统计指标
                stats.put("avgPoolUtilization", calculateAverage(chartData.get("poolUtilization")));
                stats.put("maxPoolUtilization", calculateMax(chartData.get("poolUtilization")));
                stats.put("avgQueueUtilization", calculateAverage(chartData.get("queueUtilization")));
                stats.put("maxQueueUtilization", calculateMax(chartData.get("queueUtilization")));
                stats.put("avgThroughput", calculateAverage(chartData.get("throughput")));
                stats.put("avgHealthScore", calculateAverage(chartData.get("healthScore")));
                
                stats.put("success", true);
            } else {
                stats.put("success", false);
                stats.put("error", "无法获取历史数据");
            }
            
        } catch (Exception e) {
            log.error("获取实时统计数据失败", e);
            stats.put("success", false);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * 清理过期数据
     */
    @Scheduled(cron = "0 0 */4 * * *") // 每4小时执行一次
    public void cleanupExpiredData() {
        try {
            long cutoffTime = System.currentTimeMillis() - (MAX_HISTORY_MINUTES * 60 * 1000L);
            
            // 查找并删除过期的监控数据
            Set<String> metricsKeys = redisTemplate.keys(METRICS_KEY_PREFIX + "*");
            if (metricsKeys != null) {
                long deletedCount = 0;
                for (String key : metricsKeys) {
                    try {
                        String timestampStr = key.substring(METRICS_KEY_PREFIX.length());
                        long timestamp = Long.parseLong(timestampStr);
                        if (timestamp < cutoffTime) {
                            redisTemplate.delete(key);
                            deletedCount++;
                        }
                    } catch (NumberFormatException e) {
                        // 忽略格式错误的键
                    }
                }
                
                if (deletedCount > 0) {
                    log.info("清理过期监控数据: {} 条", deletedCount);
                }
            }
            
            // 清理过期的健康检查数据
            Set<String> healthKeys = redisTemplate.keys(HEALTH_KEY_PREFIX + "*");
            if (healthKeys != null) {
                long deletedCount = 0;
                for (String key : healthKeys) {
                    try {
                        String timestampStr = key.substring(HEALTH_KEY_PREFIX.length());
                        long timestamp = Long.parseLong(timestampStr);
                        if (timestamp < cutoffTime) {
                            redisTemplate.delete(key);
                            deletedCount++;
                        }
                    } catch (NumberFormatException e) {
                        // 忽略格式错误的键
                    }
                }
                
                if (deletedCount > 0) {
                    log.info("清理过期健康数据: {} 条", deletedCount);
                }
            }
            
        } catch (Exception e) {
            log.error("清理过期数据失败", e);
        }
    }

    // 辅助方法
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private double calculateAverage(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        
        double sum = 0.0;
        int count = 0;
        
        for (Object value : values) {
            if (value instanceof Number) {
                sum += ((Number) value).doubleValue();
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0.0;
    }

    private double calculateMax(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        
        double max = Double.MIN_VALUE;
        
        for (Object value : values) {
            if (value instanceof Number) {
                double num = ((Number) value).doubleValue();
                if (num > max) {
                    max = num;
                }
            }
        }
        
        return max == Double.MIN_VALUE ? 0.0 : max;
    }
}