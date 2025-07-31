#!/bin/bash

echo "=== Java语法验证脚本 ==="

# 验证Java文件语法
echo "1. 验证主要修改文件的语法..."

# 检查WebSocketFrameworkConfig.java
echo "   检查 WebSocketFrameworkConfig.java"
if javac -cp "/tmp" -d "/tmp" src/main/java/com/framework/websocket/config/WebSocketFrameworkConfig.java 2>/dev/null; then
    echo "   ✓ WebSocketFrameworkConfig.java 语法正确"
else
    echo "   ✗ WebSocketFrameworkConfig.java 存在语法错误"
fi

# 检查ThreadPoolMonitor.java
echo "   检查 ThreadPoolMonitor.java"
if javac -cp "/tmp" -d "/tmp" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitor.java 2>/dev/null; then
    echo "   ✓ ThreadPoolMonitor.java 语法正确"  
else
    echo "   ✗ ThreadPoolMonitor.java 存在语法错误"
fi

# 检查ThreadPoolHealthChecker.java
echo "   检查 ThreadPoolHealthChecker.java"
if javac -cp "/tmp" -d "/tmp" src/main/java/com/framework/websocket/health/ThreadPoolHealthChecker.java 2>/dev/null; then
    echo "   ✓ ThreadPoolHealthChecker.java 语法正确"
else
    echo "   ✗ ThreadPoolHealthChecker.java 存在语法错误"
fi

echo ""
echo "2. 检查括号匹配..."
for file in src/main/java/com/framework/websocket/config/WebSocketFrameworkConfig.java \
            src/main/java/com/framework/websocket/monitor/ThreadPoolMonitor.java \
            src/main/java/com/framework/websocket/health/ThreadPoolHealthChecker.java; do
    open_braces=$(grep -o '{' "$file" | wc -l)
    close_braces=$(grep -o '}' "$file" | wc -l)
    if [ $open_braces -eq $close_braces ]; then
        echo "   ✓ $file 括号匹配"
    else
        echo "   ✗ $file 括号不匹配 (open: $open_braces, close: $close_braces)"
    fi
done

echo ""
echo "=== 语法验证完成 ==="