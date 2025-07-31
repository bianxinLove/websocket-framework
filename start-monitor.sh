#!/bin/bash

echo "=== WebSocket框架线程池监控启动脚本 ==="

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2)
echo "检测到Java版本: $JAVA_VERSION"

# 设置JVM参数
JVM_ARGS=""

# 根据Java版本设置不同的参数
if [[ "$JAVA_VERSION" > "1.8" ]]; then
    echo "检测到Java 9+，添加模块访问权限..."
    JVM_ARGS="--add-opens=java.management/sun.management=ALL-UNNAMED"
    
    if [[ "$JAVA_VERSION" > "16" ]]; then
        echo "检测到Java 17+，添加额外的模块访问权限..."
        JVM_ARGS="$JVM_ARGS --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
    fi
fi

echo "JVM参数: $JVM_ARGS"

# 启动应用
echo "启动WebSocket框架..."
if [ -n "$JVM_ARGS" ]; then
    mvn spring-boot:run -Dspring-boot.run.jvmArguments="$JVM_ARGS"
else
    mvn spring-boot:run
fi

echo ""
echo "应用启动完成！"
echo "监控界面访问地址："
echo "  http://localhost:8080/monitor"
echo "  http://localhost:8080/threadpool-monitor.html"
echo ""
echo "API接口："
echo "  GET  http://localhost:8080/api/monitor/threadpool/metrics"
echo "  GET  http://localhost:8080/api/monitor/threadpool/health"
echo "  GET  http://localhost:8080/api/monitor/threadpool/overview"
echo ""