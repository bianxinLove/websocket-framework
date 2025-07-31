#!/bin/bash

echo "验证Java代码语法..."

# 检查Java文件语法
echo "检查ThreadPoolMonitorWebSocketService.java..."
if grep -q "public class ThreadPoolMonitorWebSocketService" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorWebSocketService.java; then
    echo "✅ ThreadPoolMonitorWebSocketService.java 基本语法正确"
else
    echo "❌ ThreadPoolMonitorWebSocketService.java 语法可能有问题"
fi

# 检查HTML文件
echo "检查monitor.html..."
if grep -q "<!DOCTYPE html>" src/main/resources/static/monitor.html; then
    echo "✅ monitor.html 基本结构正确"
else
    echo "❌ monitor.html 结构可能有问题"
fi

# 检查JavaScript语法
echo "检查JavaScript语法..."
if grep -q "function addMemoryDataPoint" src/main/resources/static/monitor.html; then
    echo "✅ addMemoryDataPoint函数已添加"
else
    echo "❌ addMemoryDataPoint函数缺失"
fi

if grep -q "broadcastWebSocketMetrics" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorWebSocketService.java; then
    echo "✅ broadcastWebSocketMetrics方法已添加"
else
    echo "❌ broadcastWebSocketMetrics方法缺失"
fi

if grep -q "broadcastMemoryStatus" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorWebSocketService.java; then
    echo "✅ broadcastMemoryStatus方法已添加"
else
    echo "❌ broadcastMemoryStatus方法缺失"
fi

echo "验证完成！"