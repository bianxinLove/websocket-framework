## TimeoutTaskWrapper任务名称优化效果对比

### 🔧 **优化前的日志格式**：
```
心跳检测任务被拒绝，队列可能过载: userId=user123
任务执行超时: taskName=消息处理-user123-1234567, timeoutSeconds=300
任务执行完成: taskName=心跳检测-user456, duration=1200ms
```

### ✅ **优化后的日志格式**：
```
心跳检测任务被拒绝，队列可能过载: service=chatroom, userId=user123
任务执行超时: task=消息处理[chatroom:user123]-1234567, timeout=300s
任务执行完成: task=心跳检测[notification:user456], duration=1200ms
队列过载，拒绝提交任务: task=初始心跳发送[live:user789], queueSize=3200, activeCount=45
线程池繁忙，延迟提交任务: task=定期心跳检测[websocket:user101], activeCount=40/50
```

### 📊 **优化带来的好处**：

#### 1. **更清晰的服务区分**
- **原来**: `心跳检测-user123` (不知道是哪个服务)
- **现在**: `心跳检测[chatroom:user123]` (清楚知道是聊天室服务)

#### 2. **便于问题定位**
- **可以快速筛选特定服务**: 搜索 `[chatroom:` 找所有聊天室相关任务
- **可以追踪用户在多服务中的活动**: 搜索 `user123` 看该用户在所有服务中的任务状态
- **便于监控告警**: 根据服务类型设置不同的告警策略

#### 3. **统一的命名规范**
```java
// 心跳相关任务
"初始心跳发送[service:userId]"
"心跳检测[service:userId]" 
"定期心跳检测[service:userId]"

// 消息处理任务
"消息处理[service:userId]-messageHash"

// 批量任务
"批量任务[service:userId]-batchId"
```

#### 4. **更好的运维体验**
- **日志聚合**: 可以按服务维度聚合分析日志
- **性能监控**: 可以分析不同服务的任务执行情况
- **容量规划**: 了解各服务的资源使用情况

### 💡 **实际应用场景**：

```bash
# 查看聊天室服务的所有任务
grep "\\[chatroom:" application.log

# 查看某用户的所有任务活动
grep "user123" application.log

# 查看超时的任务情况
grep "任务执行超时" application.log | grep -o "\\[[^\\]]*\\]"

# 监控队列过载情况
grep "队列过载" application.log | awk '{print $NF}' | sort | uniq -c
```

这样的优化让系统监控和问题排查变得更加高效！