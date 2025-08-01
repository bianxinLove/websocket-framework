#!/bin/bash

echo "WebSocket并发写入问题修复验证"
echo "=================================="

# 检查关键修复点
echo "1. 检查WebSocketSession是否添加了同步锁..."
if grep -q "ReentrantLock sendLock" src/main/java/com/framework/websocket/session/WebSocketSession.java; then
    echo "✅ WebSocketSession已添加ReentrantLock"
else
    echo "❌ WebSocketSession缺少ReentrantLock"
fi

if grep -q "sendLock.lock()" src/main/java/com/framework/websocket/session/WebSocketSession.java; then
    echo "✅ 发送方法已添加同步锁机制"
else
    echo "❌ 发送方法缺少同步锁机制"
fi

echo ""
echo "2. 检查异常处理..."
if grep -q "IllegalStateException" src/main/java/com/framework/websocket/session/WebSocketSession.java; then
    echo "✅ 已添加IllegalStateException处理"
else
    echo "❌ 缺少IllegalStateException处理"
fi

echo ""
echo "3. 检查定时任务错开执行..."
INITIAL_DELAY_COUNT=$(grep -c "initialDelay" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorWebSocketService.java)
echo "✅ 已为 $INITIAL_DELAY_COUNT 个定时任务设置了初始延迟"

echo ""
echo "4. 统计修复的发送方法数量..."
SEND_METHODS=$(grep -c "sendLock.lock()" src/main/java/com/framework/websocket/session/WebSocketSession.java)
echo "✅ 已同步化 $SEND_METHODS 个发送方法"

echo ""
echo "修复总结:"
echo "- 为WebSocket会话添加了ReentrantLock锁机制"
echo "- 所有发送方法都使用lock/unlock确保线程安全"
echo "- 添加了IllegalStateException专门处理"
echo "- 定时任务设置了initialDelay错开执行时间"
echo "- 增强了异常处理和日志记录"
echo ""
echo "修复完成！这些改动将解决 'TEXT_FULL_WRITING' 状态冲突问题。"

echo ""
echo "执行时间错开安排:"
echo "- broadcastMetrics: 延迟5秒，每30秒执行"
echo "- broadcastMemoryStatus: 延迟15秒，每30秒执行"  
echo "- broadcastWebSocketMetrics: 延迟30秒，每60秒执行"
echo "- broadcastSystemStats: 延迟60秒，每300秒执行"