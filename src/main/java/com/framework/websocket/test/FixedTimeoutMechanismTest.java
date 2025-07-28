package com.framework.websocket.test;

import com.framework.websocket.util.TimeoutTaskWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 验证修复后的超时机制效果
 * 
 * @author bianxin
 */
@Slf4j
@Component
public class FixedTimeoutMechanismTest {
    
    public void testFixedTimeoutBehavior() {
        log.info("=== 验证修复后的超时机制 ===");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
        TimeoutTaskWrapper wrapper = new TimeoutTaskWrapper(executor, 2); // 2秒超时
        
        // 测试1: 正常任务（不会超时）
        testNormalTask(wrapper);
        
        // 测试2: 超时任务（应该被正确处理）
        testTimeoutTask(wrapper);
        
        // 测试3: 超时后的日志控制
        testTimeoutLogControl(wrapper);
        
        // 等待所有测试完成
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        wrapper.shutdown();
        executor.shutdown();
        
        log.info("=== 超时机制测试完成 ===");
    }
    
    /**
     * 测试正常任务
     */
    private void testNormalTask(TimeoutTaskWrapper wrapper) {
        log.info("--- 测试正常任务 ---");
        
        CompletableFuture<Void> future = wrapper.executeWithTimeout(() -> {
            try {
                Thread.sleep(1000); // 1秒，不会超时
                log.info("正常任务执行完成（应该看到这条日志）");
            } catch (InterruptedException e) {
                log.info("正常任务被中断");
                Thread.currentThread().interrupt();
            }
        }, "正常任务测试[chatroom:user123]");
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.warn("正常任务异常: {}", throwable.getClass().getSimpleName());
            } else {
                log.info("正常任务顺利完成");
            }
        });
    }
    
    /**
     * 测试超时任务
     */
    private void testTimeoutTask(TimeoutTaskWrapper wrapper) {
        log.info("--- 测试超时任务 ---");
        
        CompletableFuture<Void> future = wrapper.executeWithTimeout(() -> {
            try {
                Thread.sleep(5000); // 5秒，会超时
                log.info("超时任务执行完成（修复后不应该看到这条日志）");
            } catch (InterruptedException e) {
                log.info("超时任务被正确中断");
                Thread.currentThread().interrupt();
            }
        }, "超时任务测试[notification:user456]");
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.info("超时任务被取消: {}", throwable.getClass().getSimpleName());
            } else {
                log.info("超时任务意外完成");
            }
        });
    }
    
    /**
     * 测试超时后的日志控制
     */
    private void testTimeoutLogControl(TimeoutTaskWrapper wrapper) {
        log.info("--- 测试超时后的日志控制 ---");
        
        CompletableFuture<Void> future = wrapper.executeWithTimeout(() -> {
            try {
                // 模拟一个不响应中断的长时间任务
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 10000) {
                    // 忙等待，不检查中断状态
                    Math.sqrt(Math.random());
                }
                log.info("忙等待任务完成（修复后应该被忽略）");
            } catch (Exception e) {
                log.info("忙等待任务异常: {}", e.getMessage());
            }
        }, "忙等待任务测试[live:user789]");
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.info("忙等待任务结果: {}", throwable.getClass().getSimpleName());
            } else {
                log.info("忙等待任务意外完成");
            }
        });
    }
    
    /**
     * 测试智能提交的超时控制
     */
    public void testSmartSubmitTimeout() {
        log.info("=== 测试智能提交的超时控制 ===");
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        TimeoutTaskWrapper wrapper = new TimeoutTaskWrapper(executor, 1); // 1秒超时
        
        // 快速提交多个任务，测试超时控制
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            boolean submitted = wrapper.smartSubmit(() -> {
                try {
                    Thread.sleep(2000); // 2秒，会超时
                    log.info("智能提交任务{}完成（不应该看到）", taskId);
                } catch (InterruptedException e) {
                    log.info("智能提交任务{}被中断", taskId);
                    Thread.currentThread().interrupt();
                }
            }, String.format("智能提交测试[service:user%d]", taskId));
            
            log.info("智能提交任务{}: {}", taskId, submitted ? "成功" : "拒绝");
        }
        
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        wrapper.shutdown();
        executor.shutdown();
    }
}