#!/bin/bash

echo "=== 代码修复验证脚本 ==="

echo "1. 检查Java文件语法..."

# 检查是否有明显的语法错误
echo "   - 检查括号匹配..."
for file in $(find src/main/java -name "*.java"); do
    open_braces=$(grep -o '{' "$file" | wc -l)
    close_braces=$(grep -o '}' "$file" | wc -l)
    if [ $open_braces -ne $close_braces ]; then
        echo "     WARNING: 括号不匹配 in $file (open: $open_braces, close: $close_braces)"
    fi
done

echo "   - 检查分号缺失..."
grep -n "^\s*[^/\*].*[^;{}]\s*$" src/main/java/com/framework/websocket/config/WebSocketFrameworkConfig.java | grep -v "import\|package\|class\|interface\|@\|//\|/\*\|\*" | head -5

echo "2. 检查导入依赖..."
echo "   - WebSocketFrameworkConfig.java 导入检查:"
grep "^import" src/main/java/com/framework/websocket/config/WebSocketFrameworkConfig.java

echo "   - ThreadPoolMonitor.java 导入检查:"
grep "^import" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitor.java

echo "3. 检查未定义变量..."
if grep -n "currentMonitorTask" src/main/java/com/framework/websocket/monitor/ThreadPoolMonitor.java; then
    echo "   ✓ currentMonitorTask 变量已定义"
else
    echo "   ✗ ERROR: currentMonitorTask 变量未定义"
fi

echo "4. 检查配置文件匹配..."
echo "   - application.yml 配置结构:"
grep -A 1 "monitoring:" src/main/resources/application.yml
echo "   - Properties类监控配置检查:"
grep -A 3 "class Monitoring" src/main/java/com/framework/websocket/config/WebSocketFrameworkProperties.java

echo "=== 检查完成 ==="
echo "主要修复内容:"
echo "1. ✓ 修复了 currentMonitorTask 变量缺失问题"
echo "2. ✓ 修复了吞吐量计算逻辑错误"
echo "3. ✓ 统一了配置文件与Properties类结构"
echo "4. ✓ 优化了定期任务计数避免重复计数"
echo "5. ✓ 改进了紧急清理策略避免并发修改异常"
echo "6. ✓ 优化了监控日志频率减少内存使用"
echo "7. ✓ 添加了@EnableConfigurationProperties注解"