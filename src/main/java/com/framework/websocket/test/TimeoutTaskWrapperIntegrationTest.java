package com.framework.websocket.test;

import com.framework.websocket.util.TimeoutTaskWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TimeoutTaskWrapper集成测试
 * 验证在WebSocket心跳检测中的实际效果
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class TimeoutTaskWrapperIntegrationTest implements CommandLineRunner {
    
    @Autowired
    @Qualifier("timeoutTaskWrapper")
    private TimeoutTaskWrapper timeoutTaskWrapper;
    
    @Override
    public void run(String... args) throws Exception {
        if (!"test".equals(System.getProperty("spring.profiles.active"))) {
            // 只在测试环境下运行
            return;
        }
        
        log.info("开始TimeoutTaskWrapper集成测试...");
        
        // 测试1: 基本超时功能
        testBasicTimeout();
        
        // 测试2: 智能提交功能
        testSmartSubmit();
        
        // 测试3: 批量任务处理
        testBatchProcessing();
        
        // 测试4: 模拟心跳检测场景
        testHeartbeatScenario();
        
        log.info("TimeoutTaskWrapper集成测试完成");
    }
    
    /**
     * 测试基本超时功能
     */
    private void testBasicTimeout() throws InterruptedException {
        log.info("=== 测试基本超时功能 ===");
        
        CountDownLatch latch = new CountDownLatch(2);
        
        // 正常任务
        CompletableFuture<Void> normalTask = timeoutTaskWrapper.executeWithTimeout(() -> {
            try {
                Thread.sleep(1000);
                log.info("正常任务执行完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("正常任务被中断");
            }
        }, "正常任务测试");
        
        normalTask.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.warn("正常任务异常: {}", throwable.getMessage());
            }
            latch.countDown();
        });
        
        // 超时任务
        CompletableFuture<Void> timeoutTask = timeoutTaskWrapper.executeWithTimeout(() -> {
            try {
                Thread.sleep(10000); // 10秒，会超时
                log.info("超时任务执行完成（不应该看到这条日志）");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("超时任务被中断");
            }
        }, "超时任务测试");
        
        timeoutTask.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.info("超时任务被取消: {}", throwable.getClass().getSimpleName());
            }
            latch.countDown();
        });
        
        // 等待任务完成
        latch.await(15, TimeUnit.SECONDS);
        
        log.info("基本超时功能测试完成");
    }
    
    /**
     * 测试智能提交功能
     */
    private void testSmartSubmit() throws InterruptedException {
        log.info("=== 测试智能提交功能 ===");
        
        // 快速提交多个任务，测试智能提交的队列保护
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            boolean submitted = timeoutTaskWrapper.smartSubmit(() -> {
                try {
                    Thread.sleep(200);
                    log.debug("智能提交任务{}完成", taskId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "智能提交测试-" + taskId);
            
            if (!submitted) {
                log.warn("智能提交任务{}被拒绝", taskId);
            }
        }
        
        // 等待任务执行
        Thread.sleep(5000);
        
        log.info("智能提交功能测试完成");
    }
    
    /**
     * 测试批量任务处理
     */
    private void testBatchProcessing() throws InterruptedException {
        log.info("=== 测试批量任务处理 ===");
        
        CountDownLatch batchLatch = new CountDownLatch(3);
        
        // 批量任务1: 成功任务
        timeoutTaskWrapper.executeBatchWithTimeout(() -> {
            log.info("批量任务1执行成功");
            batchLatch.countDown();
        }, "批量任务成功测试", 3);
        
        // 批量任务2: 需要重试的任务
        timeoutTaskWrapper.executeBatchWithTimeout(() -> {
            if (Math.random() < 0.8) {
                throw new RuntimeException("模拟任务失败");
            }
            log.info("批量任务2重试成功");
            batchLatch.countDown();
        }, "批量任务重试测试", 5);
        
        // 批量任务3: 最终失败的任务
        timeoutTaskWrapper.executeBatchWithTimeout(() -> {
            throw new RuntimeException("模拟任务总是失败");
        }, "批量任务失败测试", 2);
        
        batchLatch.countDown(); // 为失败任务减一
        
        // 等待批量任务完成
        batchLatch.await(10, TimeUnit.SECONDS);
        
        log.info("批量任务处理测试完成");
    }
    
    /**
     * 测试心跳检测场景
     */
    private void testHeartbeatScenario() throws InterruptedException {
        log.info("=== 测试心跳检测场景 ===");
        
        // 模拟多个用户的心跳检测
        CountDownLatch heartbeatLatch = new CountDownLatch(5);
        
        for (int userId = 1; userId <= 5; userId++) {
            final String userIdStr = "user" + userId;
            
            // 模拟心跳检测任务
            timeoutTaskWrapper.executeWithTimeout(() -> {
                try {
                    // 模拟心跳检测逻辑
                    simulateHeartbeatCheck(userIdStr);
                    log.info("用户{}心跳检测完成", userIdStr);
                } catch (Exception e) {
                    log.error("用户{}心跳检测失败", userIdStr, e);
                } finally {
                    heartbeatLatch.countDown();
                }
            }, "心跳检测-" + userIdStr);
        }
        
        // 等待所有心跳检测完成
        heartbeatLatch.await(10, TimeUnit.SECONDS);
        
        log.info("心跳检测场景测试完成");
    }
    
    /**
     * 模拟心跳检测逻辑
     */
    private void simulateHeartbeatCheck(String userId) throws InterruptedException {
        // 模拟心跳检测耗时
        Thread.sleep(500 + (int)(Math.random() * 1000));
        
        // 模拟可能的异常情况
        if (Math.random() < 0.1) {
            throw new RuntimeException("心跳检测网络异常");
        }
        
        log.debug("用户{}心跳状态正常", userId);
    }
}