package com.framework.websocket.example;

import com.framework.websocket.util.TimeoutTaskWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

/**
 * TimeoutTaskWrapper使用示例
 * 展示如何在项目中正确使用任务超时包装器
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class TimeoutTaskWrapperExample {
    
    @Autowired
    @Qualifier("timeoutTaskWrapper")
    private TimeoutTaskWrapper timeoutTaskWrapper;
    
//    @PostConstruct
    public void demonstrateUsage() {
        log.info("开始演示TimeoutTaskWrapper的正确使用方法");
        
        // 1. 基本超时任务执行
        executeBasicTimeoutTask();
        
        // 2. 智能任务提交
        demonstrateSmartSubmit();
        
        // 3. 批量任务处理
        demonstrateBatchProcessing();
    }
    
    /**
     * 演示基本的超时任务执行
     */
    private void executeBasicTimeoutTask() {
        log.info("=== 演示基本超时任务执行 ===");
        
        // 正常任务（不会超时）
        CompletableFuture<Void> normalTask = timeoutTaskWrapper.executeWithTimeout(() -> {
            try {
                Thread.sleep(1000); // 模拟1秒任务
                log.info("正常任务执行完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "正常任务");
        
        // 超时任务（会被取消）
        CompletableFuture<Void> timeoutTask = timeoutTaskWrapper.executeWithTimeout(() -> {
            try {
                Thread.sleep(10000); // 模拟10秒任务，会超时
                log.info("超时任务执行完成（不应该看到这条日志）");
            } catch (InterruptedException e) {
                log.info("超时任务被中断");
                Thread.currentThread().interrupt();
            }
        }, "超时任务");
        
        // 监听任务完成
        normalTask.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("正常任务执行失败", throwable);
            } else {
                log.info("正常任务执行成功");
            }
        });
        
        timeoutTask.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.warn("超时任务被取消或失败: {}", throwable.getMessage());
            } else {
                log.info("超时任务执行成功");
            }
        });
    }
    
    /**
     * 演示智能任务提交
     */
    private void demonstrateSmartSubmit() {
        log.info("=== 演示智能任务提交 ===");
        
        // 智能提交会根据线程池状态决定是否执行
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            boolean submitted = timeoutTaskWrapper.smartSubmit(() -> {
                try {
                    Thread.sleep(500);
                    log.debug("智能任务{}执行完成", taskId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "智能任务-" + taskId);
            
            if (!submitted) {
                log.warn("智能任务{}被拒绝提交", taskId);
            }
        }
    }
    
    /**
     * 演示批量任务处理
     */
    private void demonstrateBatchProcessing() {
        log.info("=== 演示批量任务处理 ===");
        
        // 批量任务具有重试机制
        timeoutTaskWrapper.executeBatchWithTimeout(() -> {
            // 模拟可能失败的任务
            if (Math.random() < 0.7) { // 70%概率失败
                throw new RuntimeException("模拟任务失败");
            }
            log.info("批量任务执行成功");
        }, "批量任务示例", 3); // 最多重试3次
    }
    
    /**
     * 心跳检测的正确使用方式示例
     */
    public void heartbeatWithTimeout() {
        log.info("=== 心跳检测超时保护示例 ===");
        
        // 而不是直接使用scheduledExecutorService.scheduleWithFixedDelay
        // 应该这样使用TimeoutTaskWrapper
        timeoutTaskWrapper.executeWithTimeout(() -> {
            try {
                // 实际的心跳检测逻辑
                checkHeartbeat();
            } catch (Exception e) {
                log.error("心跳检测执行异常", e);
            }
        }, "心跳检测");
    }
    
    /**
     * 模拟心跳检测逻辑
     */
    private void checkHeartbeat() {
        // 模拟心跳检测
        log.debug("执行心跳检测...");
        try {
            Thread.sleep(200); // 模拟检测耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 消息处理的正确使用方式示例
     */
    public void processMessageWithTimeout(String message) {
        // 消息处理也应该使用超时保护
        timeoutTaskWrapper.executeWithTimeout(() -> {
            try {
                // 实际的消息处理逻辑
                processMessage(message);
            } catch (Exception e) {
                log.error("消息处理异常: message={}", message, e);
            }
        }, "消息处理-" + message.hashCode());
    }
    
    /**
     * 模拟消息处理逻辑
     */
    private void processMessage(String message) {
        log.debug("处理消息: {}", message);
        // 模拟处理耗时
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}