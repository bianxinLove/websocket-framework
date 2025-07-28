package com.framework.websocket.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 改进的任务超时包装器
 * 使用更高效的超时机制，避免创建过多监控任务
 * 
 * @author bianxin
 * @version 2.1.0
 */
@Slf4j
public class TimeoutTaskWrapper {
    
    private final ScheduledExecutorService executor;
    private final long timeoutSeconds;
    private final AtomicLong taskCounter = new AtomicLong(0);
    
    // 使用单独的监控线程池，避免占用主线程池资源
    private final ScheduledExecutorService monitorExecutor;
    
    public TimeoutTaskWrapper(ScheduledExecutorService executor, long timeoutSeconds) {
        this.executor = executor;
        this.timeoutSeconds = timeoutSeconds;
        
        // 创建专门的监控线程池（只需要1-2个线程）
        this.monitorExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "TimeoutMonitor-" + taskCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 执行带超时的Runnable任务
     * 使用中断感知包装器提供更好的超时控制
     */
    public CompletableFuture<Void> executeWithTimeout(Runnable task, String taskName) {
        long startTime = System.currentTimeMillis();
        
        // 创建可中断的任务包装器
        InterruptibleTaskWrapper wrapper = new InterruptibleTaskWrapper(task, taskName, startTime);
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(wrapper, executor);
        
        // 使用独立的监控线程池进行超时监控
        ScheduledFuture<?> timeoutFuture = monitorExecutor.schedule(() -> {
            if (!future.isDone()) {
                log.warn("任务执行超时: task={}, timeout={}s", taskName, timeoutSeconds);
                
                // 1. 设置中断标志
                wrapper.markAsTimedOut();
                
                // 2. 尝试取消任务
                future.cancel(true);
                
                // 3. 如果任务仍在运行，给出警告
                monitorExecutor.schedule(() -> {
                    if (!future.isDone()) {
                        log.error("任务超时后仍在执行，可能存在不可中断的操作: task={}", taskName);
                    }
                }, 5, TimeUnit.SECONDS);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        
        // 任务完成时取消超时监控
        future.whenComplete((result, throwable) -> {
            timeoutFuture.cancel(false);
            
            if (throwable != null) {
                if (throwable instanceof CancellationException) {
                    log.debug("任务被取消: task={}", taskName);
                } else if (!wrapper.isTimedOut()) {
                    log.error("任务执行失败: task={}", taskName, throwable);
                }
            }
        });
        
        return future;
    }
    
    /**
     * 可中断的任务包装器
     * 提供更好的中断检测和日志控制
     */
    private class InterruptibleTaskWrapper implements Runnable {
        private final Runnable originalTask;
        private final String taskName;
        private final long startTime;
        private final AtomicBoolean timedOut = new AtomicBoolean(false);
        
        public InterruptibleTaskWrapper(Runnable originalTask, String taskName, long startTime) {
            this.originalTask = originalTask;
            this.taskName = taskName;
            this.startTime = startTime;
        }
        
        @Override
        public void run() {
            try {
                // 检查是否在开始前就已经超时
                if (timedOut.get() || Thread.currentThread().isInterrupted()) {
                    log.debug("任务开始前已超时或中断: task={}", taskName);
                    return;
                }
                
                // 执行原始任务
                originalTask.run();
                
                // 只有在未超时的情况下才记录完成日志
                if (!timedOut.get()) {
                    long duration = System.currentTimeMillis() - startTime;
                    if (log.isDebugEnabled()) {
                        log.debug("任务执行完成: task={}, duration={}ms", taskName, duration);
                    }
                } else {
                    log.debug("任务在超时后完成，忽略结果: task={}", taskName);
                }
                
            } catch (InterruptedException e) {
                log.debug("任务被中断: task={}", taskName);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!timedOut.get()) {
                    log.error("任务执行异常: task={}", taskName, e);
                    throw new RuntimeException(e);
                } else {
                    log.debug("任务超时后发生异常（忽略）: task={}, error={}", taskName, e.getMessage());
                }
            }
        }
        
        public void markAsTimedOut() {
            timedOut.set(true);
        }
        
        public boolean isTimedOut() {
            return timedOut.get();
        }
    }
    
    /**
     * 批量执行任务时的优化版本
     * 使用单个监控任务管理多个执行任务
     */
    public void executeBatchWithTimeout(Runnable task, String taskName, int maxRetries) {
        AtomicLong executionCount = new AtomicLong(0);
        AtomicBoolean batchTimedOut = new AtomicBoolean(false);
        
        // 创建带重试和超时感知的任务
        Runnable retryableTask = new Runnable() {
            @Override
            public void run() {
                try {
                    // 检查批量任务是否已超时
                    if (batchTimedOut.get()) {
                        log.debug("批量任务已超时，跳过重试: task={}", taskName);
                        return;
                    }
                    
                    long startTime = System.currentTimeMillis();
                    task.run();
                    long duration = System.currentTimeMillis() - startTime;
                    
                    if (!batchTimedOut.get() && log.isDebugEnabled()) {
                        log.debug("批量任务执行完成: task={}, attempt={}, duration={}ms", 
                            taskName, executionCount.get(), duration);
                    }
                } catch (Exception e) {
                    if (batchTimedOut.get()) {
                        log.debug("批量任务超时后发生异常（忽略）: task={}", taskName);
                        return;
                    }
                    
                    long attempts = executionCount.incrementAndGet();
                    if (attempts < maxRetries) {
                        log.warn("批量任务执行失败，准备重试: task={}, attempt={}/{}", 
                            taskName, attempts, maxRetries, e);
                        // 延迟重试
                        executor.schedule(this, 1, TimeUnit.SECONDS);
                    } else {
                        log.error("批量任务执行失败，已达最大重试次数: task={}, maxRetries={}", 
                            taskName, maxRetries, e);
                    }
                }
            }
        };
        
        // 提交到执行器
        Future<?> future = executor.submit(retryableTask);
        
        // 使用单独的监控线程（不占用主线程池）
        monitorExecutor.schedule(() -> {
            if (!future.isDone()) {
                log.warn("批量任务执行超时，强制取消: task={}, timeout={}s", 
                    taskName, timeoutSeconds);
                batchTimedOut.set(true);
                future.cancel(true);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 智能任务提交 - 根据队列状态决定是否提交
     */
    public boolean smartSubmit(Runnable task, String taskName) {
        if (executor instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor) executor;
            
            // 检查队列状态，避免在队列过载时提交新任务
            int queueSize = stpe.getQueue().size();
            int activeCount = stpe.getActiveCount();
            
            // 如果队列过载，拒绝提交
            if (queueSize > 3000) {
                log.warn("队列过载，拒绝提交任务: task={}, queueSize={}, activeCount={}", 
                    taskName, queueSize, activeCount);
                return false;
            }
            
            // 如果活跃线程数过高，延迟提交
            if (activeCount > stpe.getMaximumPoolSize() * 0.8) {
                log.debug("线程池繁忙，延迟提交任务: task={}, activeCount={}/{}", 
                    taskName, activeCount, stpe.getMaximumPoolSize());
                
                // 延迟1秒后重试
                monitorExecutor.schedule(() -> smartSubmit(task, taskName), 1, TimeUnit.SECONDS);
                return true;
            }
        }
        
        // 正常提交
        executeWithTimeout(task, taskName);
        return true;
    }
    
    /**
     * 关闭监控线程池
     */
    public void shutdown() {
        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            monitorExecutor.shutdown();
            try {
                if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}