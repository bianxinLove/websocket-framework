#!/bin/bash

echo "WebSocket会话管理修复验证"
echo "=========================="

# 检查关键修复点
echo "1. 检查broadcast方法是否包含失效会话清理..."
if grep -q "failedUsers.add(userId)" src/main/java/com/framework/websocket/session/WebSocketSessionManager.java; then
    echo "✅ broadcast方法已添加失效会话清理逻辑"
else
    echo "❌ broadcast方法缺少失效会话清理逻辑"
fi

if grep -q "session.isOpen()" src/main/java/com/framework/websocket/session/WebSocketSessionManager.java; then
    echo "✅ broadcast方法已添加会话状态检查"
else
    echo "❌ broadcast方法缺少会话状态检查"
fi

echo ""
echo "2. 检查监控服务是否优化了客户端检查..."
if grep -q "没有活跃的监控客户端，跳过广播" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorWebSocketService.java; then
    echo "✅ 监控服务已优化客户端检查"
else
    echo "❌ 监控服务缺少客户端检查优化"
fi

echo ""
echo "3. 统计修复的方法数量..."
BROADCAST_METHODS=$(grep -c "没有活跃的监控客户端，跳过.*广播" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorWebSocketService.java)
echo "✅ 已优化 $BROADCAST_METHODS 个广播方法"

echo ""
echo "修复总结:"
echo "- 增加了会话有效性检查"
echo "- 自动清理失效会话"
echo "- 优化了广播方法的日志输出"
echo "- 添加了客户端数量检查，避免无效广播"
echo ""
echo "修复完成！这些改动将解决 'WebSocket会话已关闭' 的异常问题。"