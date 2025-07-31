# JVM启动参数配置

## 如果需要完整的CPU监控功能，可以添加以下JVM参数：

### Java 9-11
```bash
--add-opens=java.management/sun.management=ALL-UNNAMED
```

### Java 17+
```bash
--add-opens=java.management/sun.management=ALL-UNNAMED
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
```

## Maven启动时添加参数
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="--add-opens=java.management/sun.management=ALL-UNNAMED"
```

## IDE中配置JVM参数
在IDEA或Eclipse的运行配置中，添加VM options：
```
--add-opens=java.management/sun.management=ALL-UNNAMED
```

## 生产环境配置
在application.yml中添加：
```yaml
spring:
  application:
    name: websocket-framework
  profiles:
    active: production

# 生产环境JVM参数（通过环境变量设置）
# JAVA_OPTS="--add-opens=java.management/sun.management=ALL-UNNAMED"
```

## 说明
- 即使不添加这些参数，监控功能也能正常工作
- 系统会自动使用降级方案（系统负载平均值）来估算CPU使用率
- 添加参数后可以获得更精确的进程CPU使用率数据