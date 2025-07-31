package com.framework.websocket.monitor;

import com.framework.websocket.config.WebSocketFrameworkProperties;
import com.framework.websocket.session.WebSocketSessionManager;
import com.framework.websocket.session.WebSocketSessionCleaner;
import com.framework.websocket.event.WebSocketEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 智能线程池监控器
 * 采用动态配置、采样监控和JVM内置工具的最优策略
 * 
 * @author bianxin
 * @version 2.0.0
 */
@Slf4j
@Component
public class ThreadPoolMonitor {

    @Autowired
    private WebSocketFrameworkProperties properties;
    
    @Autowired
    @Qualifier("webSocketExecutorService")
    private ScheduledExecutorService monitoredThreadPool;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired 
    private WebSocketSessionCleaner sessionCleaner;
    
    // JVM内置监控工具
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    
    // 监控状态控制
    private final AtomicBoolean monitoringActive = new AtomicBoolean(true);
    private final AtomicLong samplingCounter = new AtomicLong(0);
    private final AtomicLong lastOptimizationTime = new AtomicLong(System.currentTimeMillis());
    
    // 内存监控状态
    private final AtomicBoolean isMemoryWarningMode = new AtomicBoolean(false);
    private final AtomicBoolean isMemoryCriticalMode = new AtomicBoolean(false);
    private final AtomicLong lastMemoryWarningTime = new AtomicLong(0);
    private final AtomicLong totalMemoryWarningCount = new AtomicLong(0);
    
    // 内存阈值配置
    private static final double MEMORY_WARNING_THRESHOLD = 0.75;  // 75%预警
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.90; // 90%严重
    private static final double MEMORY_RECOVERY_THRESHOLD = 0.60; // 60%恢复
    private static final long MEMORY_WARNING_INTERVAL = 60000; // 1分钟
    
    // 动态监控频率控制
    private volatile int currentMonitorInterval = 30; // 初始30秒
    private volatile int samplingRate = 1; // 初始采样率1:1
    
    // 性能统计
    private final AtomicLong totalMonitoringCost = new AtomicLong(0);
    private final AtomicLong monitoringExecutions = new AtomicLong(0);
    
    // 独立的监控调度器（避免占用主线程池）
    private ScheduledExecutorService monitorScheduler;
    
    // 当前监控任务
    private ScheduledFuture<?> currentMonitorTask;
    
    // 上一次任务完成数（用于计算吞吐量）
    private volatile long lastCompletedTaskCount = 0;
    private volatile long lastThroughputCalculationTime = System.currentTimeMillis();
    
    // 线程池健康状态
    private volatile ThreadPoolHealthStatus lastHealthStatus = ThreadPoolHealthStatus.HEALTHY;
    
    @PostConstruct
    public void initialize() {
        // 从配置中获取初始值
        WebSocketFrameworkProperties.Monitoring monitoringConfig = properties.getThreadPool().getMonitoring();
        if (monitoringConfig != null) {
            this.currentMonitorInterval = monitoringConfig.getInitialInterval();
            this.samplingRate = monitoringConfig.getInitialSamplingRate();
        }
        
        // 创建专用的监控调度器
        this.monitorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThreadPoolMonitor");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY); // 低优先级，避免影响业务线程
            return t;
        });
        
        startAdaptiveMonitoring();
        log.info("智能线程池监控器已启动，初始监控间隔: {}秒", currentMonitorInterval);
    }
    
    @PreDestroy
    public void destroy() {
        monitoringActive.set(false);
        if (monitorScheduler != null && !monitorScheduler.isShutdown()) {
            monitorScheduler.shutdown();
            try {
                if (!monitorScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitorScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("线程池监控器已关闭");
    }

    /**
     * 启动自适应监控
     */
    private void startAdaptiveMonitoring() {
        if (properties.getThreadPool().getMonitoring() != null && 
            !properties.getThreadPool().getMonitoring().isEnabled()) {
            log.info("线程池监控已禁用");
            return;
        }
        
        currentMonitorTask = monitorScheduler.scheduleWithFixedDelay(
            this::performAdaptiveMonitoring,
            currentMonitorInterval,
            currentMonitorInterval,
            TimeUnit.SECONDS
        );
    }

    // 监控执行状态控制
    private final AtomicBoolean monitoringInProgress = new AtomicBoolean(false);

    /**
     * 执行自适应监控
     */
    private void performAdaptiveMonitoring() {
        if (!monitoringActive.get() || !properties.getFeatures().isHealthCheck()) {
            return;
        }
        
        // 防止重复执行监控任务
        if (!monitoringInProgress.compareAndSet(false, true)) {
            log.debug("监控任务正在执行中，跳过本次调度");
            return;
        }
        
        long startTime = System.nanoTime();
        
        try {
            // 采样决策：根据线程池状态决定是否执行监控
            if (!shouldPerformMonitoring()) {
                return;
            }
            
            // 执行轻量级监控
            ThreadPoolMetrics metrics = collectLightweightMetrics();
            
            // 执行内存监控
            monitorMemoryUsage();
            
            // 分析健康状态
            ThreadPoolHealthStatus healthStatus = analyzeHealthStatus(metrics);
            
            // 根据健康状态调整监控策略
            adjustMonitoringStrategy(healthStatus, metrics);
            
            // 执行清理策略（如果需要）
            if (shouldPerformCleanup(healthStatus, metrics)) {
                performIntelligentCleanup(metrics);
            }
            
            // 记录监控结果
            logMonitoringResult(healthStatus, metrics);
            
            // 检查健康状态是否发生变化，发送通知
            if (lastHealthStatus != healthStatus) {
                notifyHealthStatusChange(lastHealthStatus, healthStatus);
            }
            
            lastHealthStatus = healthStatus;
            
        } catch (Exception e) {
            log.warn("线程池监控异常", e);
        } finally {
            // 统计监控性能开销
            long duration = System.nanoTime() - startTime;
            totalMonitoringCost.addAndGet(duration);
            monitoringExecutions.incrementAndGet();
            
            // 释放执行状态锁
            monitoringInProgress.set(false);
        }
    }

    /**
     * 采样决策：决定是否执行本次监控
     */
    private boolean shouldPerformMonitoring() {
        long currentSample = samplingCounter.incrementAndGet();
        
        // 基础采样率控制
        if (currentSample % samplingRate != 0) {
            return false;
        }
        
        // 在系统高负载时跳过监控
        if (isSystemUnderHighLoad()) {
            log.debug("系统高负载，跳过本次监控");
            return false;
        }
        
        return true;
    }

    /**
     * 收集轻量级指标（避免重量级操作）
     */
    private ThreadPoolMetrics collectLightweightMetrics() {
        ThreadPoolMetrics metrics = new ThreadPoolMetrics();
        
        if (monitoredThreadPool instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor) monitoredThreadPool;
            
            // 快速获取基础指标
            metrics.corePoolSize = stpe.getCorePoolSize();
            metrics.maximumPoolSize = stpe.getMaximumPoolSize();
            metrics.activeCount = stpe.getActiveCount();
            metrics.queueSize = stpe.getQueue().size();
            metrics.completedTaskCount = stpe.getCompletedTaskCount();
            metrics.taskCount = stpe.getTaskCount();
            
            // 使用JVM内置工具获取线程信息（更高效）
            metrics.totalThreadCount = threadMXBean.getThreadCount();
            metrics.peakThreadCount = threadMXBean.getPeakThreadCount();
            
            // 计算衍生指标
            metrics.poolUtilization = (double) metrics.activeCount / metrics.maximumPoolSize;
            metrics.queueUtilization = (double) metrics.queueSize / getQueueCapacity();
            metrics.throughput = calculateThroughput(metrics.completedTaskCount);
        }
        
        metrics.timestamp = System.currentTimeMillis();
        return metrics;
    }

    /**
     * 分析线程池健康状态
     */
    private ThreadPoolHealthStatus analyzeHealthStatus(ThreadPoolMetrics metrics) {
        // 多维度健康评估
        int healthScore = 100;
        
        // 线程池利用率评估
        if (metrics.poolUtilization > 0.9) {
            healthScore -= 30;
        } else if (metrics.poolUtilization > 0.7) {
            healthScore -= 15;
        }
        
        // 队列利用率评估
        if (metrics.queueUtilization > 0.8) {
            healthScore -= 25;
        } else if (metrics.queueUtilization > 0.5) {
            healthScore -= 10;
        }
        
        // 任务积压评估
        long pendingTasks = metrics.taskCount - metrics.completedTaskCount;
        if (pendingTasks > metrics.maximumPoolSize * 10) {
            healthScore -= 20;
        }
        
        // 根据综合得分判断健康状态
        if (healthScore >= 80) {
            return ThreadPoolHealthStatus.HEALTHY;
        } else if (healthScore >= 60) {
            return ThreadPoolHealthStatus.WARNING;
        } else if (healthScore >= 40) {
            return ThreadPoolHealthStatus.CRITICAL;
        } else {
            return ThreadPoolHealthStatus.EMERGENCY;
        }
    }

    /**
     * 根据健康状态调整监控策略
     */
    private void adjustMonitoringStrategy(ThreadPoolHealthStatus healthStatus, ThreadPoolMetrics metrics) {
        WebSocketFrameworkProperties.Monitoring monitoringConfig = properties.getThreadPool().getMonitoring();
        if (monitoringConfig == null) {
            return;
        }
        
        int newInterval = currentMonitorInterval;
        int newSamplingRate = samplingRate;
        
        switch (healthStatus) {
            case HEALTHY:
                // 健康状态：降低监控频率，减少开销
                newInterval = Math.min(monitoringConfig.getMaxInterval(), currentMonitorInterval + 10);
                newSamplingRate = Math.min(5, samplingRate + 1);
                break;
                
            case WARNING:
                // 警告状态：保持正常监控频率
                newInterval = monitoringConfig.getInitialInterval();
                newSamplingRate = 2;
                break;
                
            case CRITICAL:
                // 严重状态：提高监控频率
                newInterval = Math.max(monitoringConfig.getMinInterval(), currentMonitorInterval - 10);
                newSamplingRate = 1;
                break;
                
            case EMERGENCY:
                // 紧急状态：最高监控频率
                newInterval = monitoringConfig.getMinInterval();
                newSamplingRate = 1;
                break;
        }
        
        // 平滑调整监控参数
        if (newInterval != currentMonitorInterval || newSamplingRate != samplingRate) {
            log.info("调整监控策略: 健康状态={}, 监控间隔: {}秒->{}秒, 采样率: 1:{} -> 1:{}", 
                healthStatus, currentMonitorInterval, newInterval, samplingRate, newSamplingRate);
            
            currentMonitorInterval = newInterval;
            samplingRate = newSamplingRate;
            
            // 重新调度监控任务
            rescheduleMonitoring();
        }
    }

    /**
     * 重新调度监控任务（线程安全版本）
     */
    private synchronized void rescheduleMonitoring() {
        try {
            // 先停止当前任务
            if (currentMonitorTask != null) {
                boolean cancelled = currentMonitorTask.cancel(false);
                if (!cancelled && !currentMonitorTask.isDone()) {
                    log.warn("无法取消当前监控任务，跳过重新调度");
                    return;
                }
                // 等待任务完全停止
                Thread.sleep(100);
            }
            
            // 启动新的调度任务
            currentMonitorTask = monitorScheduler.scheduleWithFixedDelay(
                this::performAdaptiveMonitoring,
                0, // 立即开始
                currentMonitorInterval,
                TimeUnit.SECONDS
            );
            
            log.debug("监控任务重新调度成功，新间隔: {}秒", currentMonitorInterval);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("监控任务重新调度被中断", e);
        } catch (Exception e) {
            log.error("重新调度监控任务失败", e);
        }
    }

    /**
     * 判断是否需要执行清理
     */
    private boolean shouldPerformCleanup(ThreadPoolHealthStatus healthStatus, ThreadPoolMetrics metrics) {
        // 在严重或紧急状态下执行清理
        if (healthStatus == ThreadPoolHealthStatus.CRITICAL || 
            healthStatus == ThreadPoolHealthStatus.EMERGENCY) {
            return true;
        }
        
        // 队列过载时执行清理
        if (metrics.queueUtilization > 0.8) {
            return true;
        }
        
        // 定期清理（每30分钟）
        long timeSinceLastOptimization = System.currentTimeMillis() - lastOptimizationTime.get();
        return timeSinceLastOptimization > 1800000; // 30分钟
    }

    /**
     * 执行智能清理
     */
    private void performIntelligentCleanup(ThreadPoolMetrics metrics) {
        if (monitoredThreadPool instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor) monitoredThreadPool;
            
            int queueSizeBefore = stpe.getQueue().size();
            
            // 1. 清理已取消的任务
            stpe.purge();
            
            int queueSizeAfterPurge = stpe.getQueue().size();
            int purgedTasks = queueSizeBefore - queueSizeAfterPurge;
            
            // 2. 在紧急情况下执行更激进的清理
            if (lastHealthStatus == ThreadPoolHealthStatus.EMERGENCY) {
                performEmergencyCleanup(stpe);
            }
            
            lastOptimizationTime.set(System.currentTimeMillis());
            
            if (purgedTasks > 0) {
                log.info("线程池清理完成: 清理前队列={}, 清理后队列={}, 清理任务数={}", 
                    queueSizeBefore, stpe.getQueue().size(), purgedTasks);
            }
        }
    }

    /**
     * 紧急清理策略
     */
    private void performEmergencyCleanup(ScheduledThreadPoolExecutor stpe) {
        BlockingQueue<Runnable> queue = stpe.getQueue();
        int originalSize = queue.size();
        
        // 移除延迟时间过长的任务
        int removedCount = 0;
        for (Runnable task : queue.toArray(new Runnable[0])) {
            if (task instanceof RunnableScheduledFuture) {
                RunnableScheduledFuture<?> scheduledTask = (RunnableScheduledFuture<?>) task;
                long delay = scheduledTask.getDelay(TimeUnit.MILLISECONDS);
                // 移除延迟超过10分钟的任务
                if (delay > 600000 && queue.remove(task)) {
                    removedCount++;
                }
            }
        }
        
        if (removedCount > 0) {
            log.warn("紧急清理移除了{}个过期任务，原队列大小：{}，清理后：{}", 
                removedCount, originalSize, queue.size());
        }
    }

    /**
     * 检查系统是否处于高负载状态
     */
    private boolean isSystemUnderHighLoad() {
        // 使用JVM内置工具快速检查
        double cpuUsage = getProcessCpuLoad();
        double memoryUsage = getHeapMemoryUsage();
        
        return cpuUsage > 0.8 || memoryUsage > 0.9;
    }

    /**
     * 获取进程CPU使用率（兼容性处理）
     */
    private double getProcessCpuLoad() {
        try {
            // 使用标准的OperatingSystemMXBean，避免直接访问sun.management包
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            
            // 检查是否是com.sun.management.OperatingSystemMXBean的实例
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                double cpuLoad = sunOsBean.getProcessCpuLoad();
                return cpuLoad >= 0 ? cpuLoad : 0.0; // 返回值为负数时表示不可用
            }
        } catch (Exception e) {
            // 如果无法访问sun.management包，使用降级方案
            log.debug("无法直接获取进程CPU使用率，使用降级方案: {}", e.getMessage());
        }
        
        // 降级方案：使用系统负载平均值估算
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double systemLoadAverage = osBean.getSystemLoadAverage();
            int processors = osBean.getAvailableProcessors();
            if (systemLoadAverage > 0 && processors > 0) {
                // 将系统负载转换为估算的CPU使用率
                double estimatedCpuLoad = Math.min(1.0, systemLoadAverage / processors);
                log.debug("使用系统负载估算CPU使用率: {:.1f}%", estimatedCpuLoad * 100);
                return estimatedCpuLoad;
            }
        } catch (Exception e) {
            log.debug("无法获取系统负载平均值: {}", e.getMessage());
        }
        
        // 最终降级方案：使用简单的线程统计估算
        try {
            java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            int activeThreadCount = threadBean.getThreadCount();
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            
            // 基于活跃线程数的简单估算（非常粗略）
            double estimatedLoad = Math.min(1.0, (double) activeThreadCount / (availableProcessors * 10));
            log.debug("使用线程数估算CPU负载: {:.1f}%", estimatedLoad * 100);
            return estimatedLoad;
        } catch (Exception e) {
            log.debug("无法通过线程数估算CPU负载: {}", e.getMessage());
        }
        
        return 0.0; // 所有方法都失败时返回0
    }

    /**
     * 获取堆内存使用率
     */
    private double getHeapMemoryUsage() {
        try {
            java.lang.management.MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            java.lang.management.MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            
            if (heapUsage.getMax() > 0) {
                return (double) heapUsage.getUsed() / heapUsage.getMax();
            }
        } catch (Exception e) {
            log.debug("无法获取堆内存使用率", e);
        }
        return 0.0;
    }

    /**
     * 计算吞吐量
     */
    private double calculateThroughput(long completedTaskCount) {
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - lastThroughputCalculationTime;
        long taskDelta = completedTaskCount - lastCompletedTaskCount;
        
        if (timeDelta > 0 && taskDelta >= 0) {
            double throughput = (double) taskDelta / (timeDelta / 1000.0);
            
            // 更新记录
            lastCompletedTaskCount = completedTaskCount;
            lastThroughputCalculationTime = currentTime;
            
            return throughput;
        }
        
        return 0.0;
    }

    /**
     * 获取队列容量
     */
    private int getQueueCapacity() {
        return properties.getThreadPool().getQueueCapacity();
    }

    /**
     * 记录监控结果
     */
    private void logMonitoringResult(ThreadPoolHealthStatus healthStatus, ThreadPoolMetrics metrics) {
        if (healthStatus != ThreadPoolHealthStatus.HEALTHY || log.isDebugEnabled()) {
            log.info("线程池监控报告: 状态={}, 核心线程={}, 活跃线程={}, 队列大小={}, 利用率={:.1f}%, 吞吐量={:.2f}task/s", 
                healthStatus, 
                metrics.corePoolSize, 
                metrics.activeCount, 
                metrics.queueSize,
                metrics.poolUtilization * 100,
                metrics.throughput);
        }
        
        // 定期报告监控性能开销（减少日志频率）
        long execCount = monitoringExecutions.get();
        if (execCount > 0 && execCount % 200 == 0) {
            double avgCostMs = totalMonitoringCost.get() / 1_000_000.0 / execCount;
            log.info("监控性能统计: 执行次数={}, 平均耗时={:.2f}ms", execCount, avgCostMs);
        }
    }

    /**
     * 通知健康状态变化
     */
    private void notifyHealthStatusChange(ThreadPoolHealthStatus oldStatus, ThreadPoolHealthStatus newStatus) {
        try {
            // 尝试获取WebSocket监控服务并发送健康状态变化通知
            ThreadPoolMonitorWebSocketService monitorService = 
                applicationContext.getBean(ThreadPoolMonitorWebSocketService.class);
            if (monitorService != null) {
                monitorService.sendHealthAlert(oldStatus, newStatus);
            }
        } catch (Exception e) {
            // 如果WebSocket服务不可用，只记录调试日志
            log.debug("无法发送健康状态变化通知: {}", e.getMessage());
        }
        
        // 记录健康状态变化日志
        log.info("线程池健康状态变化: {} -> {}", oldStatus, newStatus);
    }

    /**
     * 获取当前监控状态
     */
    public MonitoringStatus getMonitoringStatus() {
        MonitoringStatus status = new MonitoringStatus();
        status.isActive = monitoringActive.get();
        status.currentInterval = currentMonitorInterval;
        status.samplingRate = samplingRate;
        status.lastHealthStatus = lastHealthStatus;
        status.monitoringExecutions = monitoringExecutions.get();
        status.avgMonitoringCostMs = monitoringExecutions.get() > 0 ? 
            totalMonitoringCost.get() / 1_000_000.0 / monitoringExecutions.get() : 0;
        return status;
    }

    /**
     * 手动触发监控
     */
    public ThreadPoolMetrics manualMonitoring() {
        log.info("手动触发线程池监控");
        ThreadPoolMetrics metrics = collectLightweightMetrics();
        ThreadPoolHealthStatus healthStatus = analyzeHealthStatus(metrics);
        logMonitoringResult(healthStatus, metrics);
        return metrics;
    }

    /**
     * 线程池指标
     */
    public static class ThreadPoolMetrics {
        public int corePoolSize;
        public int maximumPoolSize;
        public int activeCount;
        public int queueSize;
        public long completedTaskCount;
        public long taskCount;
        public int totalThreadCount;
        public int peakThreadCount;
        public double poolUtilization;
        public double queueUtilization;
        public double throughput;
        public long timestamp;
    }

    /**
     * 线程池健康状态枚举
     */
    public enum ThreadPoolHealthStatus {
        HEALTHY("健康"),
        WARNING("警告"), 
        CRITICAL("严重"),
        EMERGENCY("紧急");
        
        private final String description;
        
        ThreadPoolHealthStatus(String description) {
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * 内存监控
     */
    private void monitorMemoryUsage() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
            
            // 检查内存阈值并触发相应动作
            checkMemoryThresholds(memoryUsageRatio, heapUsage);
            
        } catch (Exception e) {
            log.debug("内存监控异常", e);
        }
    }
    
    /**
     * 检查内存阈值并触发相应动作
     */
    private void checkMemoryThresholds(double memoryUsageRatio, MemoryUsage heapUsage) {
        long currentTime = System.currentTimeMillis();
        
        if (memoryUsageRatio >= MEMORY_CRITICAL_THRESHOLD) {
            handleCriticalMemoryPressure(memoryUsageRatio, heapUsage, currentTime);
        } else if (memoryUsageRatio >= MEMORY_WARNING_THRESHOLD) {
            handleWarningMemoryPressure(memoryUsageRatio, heapUsage, currentTime);
        } else if (memoryUsageRatio <= MEMORY_RECOVERY_THRESHOLD) {
            handleMemoryRecovery(memoryUsageRatio, currentTime);
        }
    }
    
    /**
     * 处理严重内存压力
     */
    private void handleCriticalMemoryPressure(double memoryUsageRatio, MemoryUsage heapUsage, long currentTime) {
        if (!isMemoryCriticalMode.get()) {
            isMemoryCriticalMode.set(true);
            isMemoryWarningMode.set(true);
            
            log.error("🚨 检测到严重内存压力！使用率: {:.1f}% ({}MB/{}MB)", 
                memoryUsageRatio * 100,
                heapUsage.getUsed() / 1024 / 1024,
                heapUsage.getMax() / 1024 / 1024);
            
            // 立即触发激进清理
            try {
                if (sessionCleaner != null) {
                    Object result = sessionCleaner.manualCleanup(true);
                    log.warn("严重内存压力下执行激进清理: {}", result);
                }
                
                // 清理事件对象池
                WebSocketEvent.clearPool();
                log.warn("严重内存压力下清理事件对象池");
                
                // 强制垃圾回收
                System.gc();
                
                // 更新统计
                totalMemoryWarningCount.incrementAndGet();
                
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
        if (currentTime - lastMemoryWarningTime.get() < MEMORY_WARNING_INTERVAL) {
            return;
        }
        
        if (!isMemoryWarningMode.get()) {
            isMemoryWarningMode.set(true);
            lastMemoryWarningTime.set(currentTime);
            
            log.warn("⚠️ 检测到内存压力预警！使用率: {:.1f}% ({}MB/{}MB)", 
                memoryUsageRatio * 100,
                heapUsage.getUsed() / 1024 / 1024,
                heapUsage.getMax() / 1024 / 1024);
            
            // 记录会话统计信息
            if (sessionManager != null) {
                log.warn("当前会话统计: {}", sessionManager.getMemoryStats());
            }
            log.warn("事件池统计: {}", WebSocketEvent.getPoolStats());
            
            // 触发标准清理
            try {
                if (sessionCleaner != null) {
                    Object result = sessionCleaner.manualCleanup(false);
                    log.info("内存预警触发清理: {}", result);
                }
                
                totalMemoryWarningCount.incrementAndGet();
                
            } catch (Exception e) {
                log.error("内存预警处理失败", e);
            }
        }
    }
    
    /**
     * 处理内存恢复
     */
    private void handleMemoryRecovery(double memoryUsageRatio, long currentTime) {
        if (isMemoryWarningMode.get() || isMemoryCriticalMode.get()) {
            isMemoryWarningMode.set(false);
            isMemoryCriticalMode.set(false);
            
            log.info("✅ 内存压力已恢复正常，当前使用率: {:.1f}%", memoryUsageRatio * 100);
        }
    }
    
    /**
     * 获取当前内存状态
     */
    public MemoryStatus getCurrentMemoryStatus() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
            double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
            
            MemoryStatus status = new MemoryStatus();
            status.heapUsed = heapUsage.getUsed();
            status.heapMax = heapUsage.getMax();
            status.heapUsageRatio = memoryUsageRatio;
            status.nonHeapUsed = nonHeapUsage.getUsed();
            status.isWarningMode = isMemoryWarningMode.get();
            status.isCriticalMode = isMemoryCriticalMode.get();
            status.totalWarningCount = totalMemoryWarningCount.get();
            status.sessionStats = sessionManager != null ? sessionManager.getMemoryStats() : "N/A";
            status.eventPoolStats = WebSocketEvent.getPoolStats();
            
            return status;
        } catch (Exception e) {
            log.error("获取内存状态失败", e);
            return new MemoryStatus(); // 返回默认状态
        }
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

    /**
     * 监控状态
     */
    public static class MonitoringStatus {
        public boolean isActive;
        public int currentInterval;
        public int samplingRate;
        public ThreadPoolHealthStatus lastHealthStatus;
        public long monitoringExecutions;
        public double avgMonitoringCostMs;
    }
}