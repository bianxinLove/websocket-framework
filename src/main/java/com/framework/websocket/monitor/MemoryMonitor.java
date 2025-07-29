package com.framework.websocket.monitor;

import com.framework.websocket.config.WebSocketFrameworkProperties;
import com.framework.websocket.event.WebSocketEvent;
import com.framework.websocket.session.WebSocketSessionCleaner;
import com.framework.websocket.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存监控和预警组件
 * 监控WebSocket框架的内存使用情况，在内存压力过大时触发预警和清理机制
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class MemoryMonitor {

    @Autowired
    private WebSocketFrameworkProperties properties;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private WebSocketSessionCleaner sessionCleaner;
    
    // JVM内存监控
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // 预警状态
    private final AtomicBoolean isWarningMode = new AtomicBoolean(false);
    private final AtomicBoolean isCriticalMode = new AtomicBoolean(false);
    private final AtomicLong lastWarningTime = new AtomicLong(0);
    private final AtomicLong totalWarningCount = new AtomicLong(0);
    
    // 内存阈值配置
    private static final double WARNING_THRESHOLD = 0.75;  // 75%预警
    private static final double CRITICAL_THRESHOLD = 0.90; // 90%严重
    private static final double RECOVERY_THRESHOLD = 0.60; // 60%恢复
    
    // 预警间隔（避免频繁预警）
    private static final long WARNING_INTERVAL = 60000; // 1分钟
    
    @PostConstruct
    public void initialize() {
        logInitialMemoryState();
    }

    /**
     * 定期内存监控（每30秒检查一次）
     */
    @Scheduled(fixedRate = 30000)
    public void monitorMemoryUsage() {
        if (!properties.getFeatures().isHealthCheck()) {
            return;
        }
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        // 检查内存状态变化
        checkMemoryThresholds(memoryUsageRatio, heapUsage);
        
        // 记录详细内存统计（调试模式）
        if (log.isDebugEnabled()) {
            logDetailedMemoryStats(heapUsage, memoryUsageRatio);
        }
    }

    /**
     * 检查内存阈值并触发相应动作
     */
    private void checkMemoryThresholds(double memoryUsageRatio, MemoryUsage heapUsage) {
        long currentTime = System.currentTimeMillis();
        
        if (memoryUsageRatio >= CRITICAL_THRESHOLD) {
            handleCriticalMemoryPressure(memoryUsageRatio, heapUsage, currentTime);
        } else if (memoryUsageRatio >= WARNING_THRESHOLD) {
            handleWarningMemoryPressure(memoryUsageRatio, heapUsage, currentTime);
        } else if (memoryUsageRatio <= RECOVERY_THRESHOLD) {
            handleMemoryRecovery(memoryUsageRatio, currentTime);
        }
    }

    /**
     * 处理严重内存压力
     */
    private void handleCriticalMemoryPressure(double memoryUsageRatio, MemoryUsage heapUsage, long currentTime) {
        if (!isCriticalMode.get()) {
            isCriticalMode.set(true);
            isWarningMode.set(true);
            
            log.error("🚨 检测到严重内存压力！使用率: {:.1f}% ({}MB/{}MB)", 
                memoryUsageRatio * 100,
                heapUsage.getUsed() / 1024 / 1024,
                heapUsage.getMax() / 1024 / 1024);
            
            // 立即触发激进清理
            try {
                WebSocketSessionCleaner.CleanupResult result = sessionCleaner.manualCleanup(true);
                log.warn("严重内存压力下执行激进清理: 清理会话={}, 清理Map={}", 
                    result.cleanedCount, result.mapCleanedCount);
                
                // 清理事件对象池
                WebSocketEvent.clearPool();
                log.warn("严重内存压力下清理事件对象池");
                
                // 强制垃圾回收
                System.gc();
                
                // 更新统计
                totalWarningCount.incrementAndGet();
                
            } catch (Exception e) {
                log.error("严重内存压力处理失败", e);
            }
        }
    }

    /**
     * 处理预警级内存压力
     */
    private void handleWarningMemoryPressure(double memoryUsageRatio, MemoryUsage heapUsage, long currentTime) {
        // 避免频繁预警
        if (currentTime - lastWarningTime.get() < WARNING_INTERVAL) {
            return;
        }
        
        if (!isWarningMode.get()) {
            isWarningMode.set(true);
            lastWarningTime.set(currentTime);
            
            log.warn("⚠️ 检测到内存压力预警！使用率: {:.1f}% ({}MB/{}MB)", 
                memoryUsageRatio * 100,
                heapUsage.getUsed() / 1024 / 1024,
                heapUsage.getMax() / 1024 / 1024);
            
            // 记录会话统计信息
            log.warn("当前会话统计: {}", sessionManager.getMemoryStats());
            log.warn("事件池统计: {}", WebSocketEvent.getPoolStats());
            
            // 触发标准清理
            try {
                WebSocketSessionCleaner.CleanupResult result = sessionCleaner.manualCleanup(false);
                if (result.cleanedCount > 0) {
                    log.info("内存预警触发清理: 清理会话={}, 清理Map={}", 
                        result.cleanedCount, result.mapCleanedCount);
                }
                
                totalWarningCount.incrementAndGet();
                
            } catch (Exception e) {
                log.error("内存预警处理失败", e);
            }
        }
    }

    /**
     * 处理内存恢复
     */
    private void handleMemoryRecovery(double memoryUsageRatio, long currentTime) {
        if (isWarningMode.get() || isCriticalMode.get()) {
            isWarningMode.set(false);
            isCriticalMode.set(false);
            
            log.info("✅ 内存压力已恢复正常，当前使用率: {:.1f}%", memoryUsageRatio * 100);
        }
    }

    /**
     * 记录详细内存统计
     */
    private void logDetailedMemoryStats(MemoryUsage heapUsage, double memoryUsageRatio) {
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        log.debug("内存详细统计 - 堆内存: {:.1f}% ({}MB/{}MB), 非堆内存: {}MB, 会话: {}, 事件池: {}", 
            memoryUsageRatio * 100,
            heapUsage.getUsed() / 1024 / 1024,
            heapUsage.getMax() / 1024 / 1024,
            nonHeapUsage.getUsed() / 1024 / 1024,
            sessionManager.getMemoryStats(),
            WebSocketEvent.getPoolStats());
    }

    /**
     * 记录初始内存状态
     */
    private void logInitialMemoryState() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        log.info("内存监控器已启动 - 初始堆内存使用率: {:.1f}% ({}MB/{}MB)", 
            memoryUsageRatio * 100,
            heapUsage.getUsed() / 1024 / 1024,
            heapUsage.getMax() / 1024 / 1024);
    }

    /**
     * 获取当前内存状态
     */
    public MemoryStatus getCurrentMemoryStatus() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        MemoryStatus status = new MemoryStatus();
        status.heapUsed = heapUsage.getUsed();
        status.heapMax = heapUsage.getMax();
        status.heapUsageRatio = memoryUsageRatio;
        status.nonHeapUsed = nonHeapUsage.getUsed();
        status.isWarningMode = isWarningMode.get();
        status.isCriticalMode = isCriticalMode.get();
        status.totalWarningCount = totalWarningCount.get();
        status.sessionStats = sessionManager.getMemoryStats();
        status.cleanupStats = sessionCleaner.getCleanupStats();
        status.eventPoolStats = WebSocketEvent.getPoolStats();
        
        return status;
    }

    /**
     * 手动触发内存检查
     */
    public void manualMemoryCheck() {
        log.info("手动触发内存检查");
        monitorMemoryUsage();
    }

    /**
     * 内存状态信息类
     */
    public static class MemoryStatus {
        public long heapUsed;
        public long heapMax;
        public double heapUsageRatio;
        public long nonHeapUsed;
        public boolean isWarningMode;
        public boolean isCriticalMode;
        public long totalWarningCount;
        public String sessionStats;
        public String cleanupStats;
        public String eventPoolStats;
        
        @Override
        public String toString() {
            return String.format(
                "MemoryStatus{堆内存: %.1f%% (%dMB/%dMB), 非堆内存: %dMB, 预警模式: %s, 严重模式: %s, 总预警次数: %d}",
                heapUsageRatio * 100, heapUsed / 1024 / 1024, heapMax / 1024 / 1024,
                nonHeapUsed / 1024 / 1024, isWarningMode, isCriticalMode, totalWarningCount
            );
        }
    }
}