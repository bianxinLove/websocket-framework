package com.framework.websocket.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 超时机制测试和问题分析
 * 解释为什么"不应该看到的日志"仍然被打印
 * 
 * @author bianxin
 */
@Slf4j
@Component
public class TimeoutMechanismTest {
    
    public void testTimeoutBehavior() {
        log.info("=== 超时机制行为测试 ===");
        
        // 测试1: 错误的超时理解
        testWrongTimeoutUnderstanding();
        
        // 测试2: 正确的超时机制
        testCorrectTimeoutMechanism();
        
        // 测试3: Thread.sleep vs 真正的长时间任务
        testDifferentTaskTypes();
    }
    
    /**
     * 测试1: 展示为什么会看到"不应该看到的日志"
     */
    private void testWrongTimeoutUnderstanding() {
        log.info("--- 测试1: 错误的超时理解 ---");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                log.info("任务开始执行...");
                
                // ❌ 问题在这里：Thread.sleep无法被interrupt立即中断
                Thread.sleep(10000); // 10秒
                
                // 如果sleep完成了，就会打印这条日志
                log.info("超时任务执行完成（不应该看到这条日志）");
                
            } catch (InterruptedException e) {
                log.info("任务被中断: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }, executor);
        
        // 2秒后取消任务
        executor.schedule(() -> {
            if (!future.isDone()) {
                log.warn("任务超时，尝试取消");
                boolean cancelled = future.cancel(true); // mayInterruptIfRunning=true
                log.info("取消结果: {}", cancelled);
            }
        }, 2, TimeUnit.SECONDS);
        
        try {
            Thread.sleep(12000); // 等待观察结果
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
    }
    
    /**
     * 测试2: 正确的超时机制
     */
    private void testCorrectTimeoutMechanism() {
        log.info("--- 测试2: 正确的超时机制 ---");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        AtomicBoolean shouldContinue = new AtomicBoolean(true);
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                log.info("改进版任务开始执行...");
                
                // ✅ 正确做法：检查中断状态，可响应的长时间任务
                for (int i = 0; i < 100; i++) {
                    if (Thread.currentThread().isInterrupted() || !shouldContinue.get()) {
                        log.info("任务检测到中断/超时信号，提前退出");
                        return;
                    }
                    
                    // 模拟工作，每次100ms
                    Thread.sleep(100);
                }
                
                log.info("改进版任务执行完成");
                
            } catch (InterruptedException e) {
                log.info("改进版任务被中断: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }, executor);
        
        // 2秒后取消任务
        executor.schedule(() -> {
            if (!future.isDone()) {
                log.warn("改进版任务超时，尝试取消");
                shouldContinue.set(false); // 设置停止标志
                boolean cancelled = future.cancel(true);
                log.info("改进版取消结果: {}", cancelled);
            }
        }, 2, TimeUnit.SECONDS);
        
        try {
            Thread.sleep(5000); // 等待观察结果
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
    }
    
    /**
     * 测试3: 不同类型任务的超时行为
     */
    private void testDifferentTaskTypes() {
        log.info("--- 测试3: 不同类型任务的超时行为 ---");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        
        // 任务A: 无法中断的Thread.sleep
        CompletableFuture<Void> taskA = CompletableFuture.runAsync(() -> {
            try {
                log.info("任务A: 开始不可中断的sleep");
                Thread.sleep(5000);
                log.info("任务A: 完成（可能会看到这条日志）");
            } catch (InterruptedException e) {
                log.info("任务A: 被中断");
                Thread.currentThread().interrupt();
            }
        }, executor);
        
        // 任务B: 可响应中断的循环
        CompletableFuture<Void> taskB = CompletableFuture.runAsync(() -> {
            try {
                log.info("任务B: 开始可中断的循环");
                for (int i = 0; i < 50; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("任务B: 检测到中断，退出");
                        return;
                    }
                    Thread.sleep(100); // 每次100ms，更容易被中断
                }
                log.info("任务B: 完成");
            } catch (InterruptedException e) {
                log.info("任务B: 被中断");
                Thread.currentThread().interrupt();
            }
        }, executor);
        
        // 任务C: 计算密集型（无法中断）
        CompletableFuture<Void> taskC = CompletableFuture.runAsync(() -> {
            log.info("任务C: 开始计算密集型任务");
            long sum = 0;
            for (long i = 0; i < 1000000000L; i++) {
                sum += i;
                // 不检查中断状态，无法被中断
            }
            log.info("任务C: 完成，结果={}", sum);
        }, executor);
        
        // 2秒后全部取消
        executor.schedule(() -> {
            log.warn("开始取消所有任务");
            taskA.cancel(true);
            taskB.cancel(true);
            taskC.cancel(true);
        }, 2, TimeUnit.SECONDS);
        
        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
    }
}