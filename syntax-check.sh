#!/bin/bash

echo "开始编译检查..."

# 检查Java源码语法
echo "检查Java语法错误..."

# 查找所有Java文件并进行语法检查
find src/main/java -name "*.java" -type f | while read -r file; do
    echo "检查文件: $file"
    
    # 基本语法检查 - 查找常见错误模式
    if grep -n "import.*\*\*" "$file" > /dev/null 2>&1; then
        echo "  ❌ 发现非法import语句"
    fi
    
    # 检查括号匹配
    open_braces=$(grep -o "{" "$file" | wc -l)
    close_braces=$(grep -o "}" "$file" | wc -l)
    if [ "$open_braces" -ne "$close_braces" ]; then
        echo "  ⚠️  大括号可能不匹配: { $open_braces 个, } $close_braces 个"
    fi
    
    # 检查Java基本语法错误
    if grep -n "class.*{.*{" "$file" > /dev/null 2>&1; then
        echo "  ⚠️  可能存在类定义语法错误"
    fi
done

echo "语法检查完成!"

# 检查关键类是否存在
echo "检查关键组件..."

key_files=(
    "src/main/java/com/framework/websocket/monitor/ThreadPoolMonitor.java"
    "src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorWebSocketService.java"
    "src/main/java/com/framework/websocket/monitor/ThreadPoolMonitorController.java"
    "src/main/java/com/framework/websocket/monitor/ThreadPoolMetricsStore.java"
    "src/main/java/com/framework/websocket/health/ThreadPoolHealthChecker.java"
    "src/main/resources/static/threadpool-monitor.html"
)

for file in "${key_files[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ $file - 存在"
    else
        echo "❌ $file - 缺失"
    fi
done

echo "检查完成!"