# 🚀 企业级WebSocket框架

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/)
[![Redis](https://img.shields.io/badge/Redis-6.0+-red.svg)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-green.svg)]()

**生产就绪的WebSocket框架** - 集成完整的连接管理、事件处理、分布式会话、智能监控等企业级功能。

## ✨ 核心特性

### 🏗️ 架构设计
- **事件驱动架构**: 基于Google Guava EventBus的异步事件处理
- **多层级会话管理**: service -> userId -> session 三层映射结构
- **拦截器链模式**: 支持可插拔的事件拦截和处理
- **静态依赖注入**: 巧妙解决WebSocket端点实例化问题

### 🔧 核心功能  
- **智能心跳检测**: 自适应心跳间隔，支持超时保护和降级策略
- **分布式会话**: Redis分布式缓存支持集群部署
- **任务超时控制**: 智能任务包装器防止任务卡死
- **会话自动清理**: 内存泄漏预防和资源回收

### 📊 监控运维
- **自适应监控**: 根据系统健康状态动态调整监控频率
- **内存压力监控**: 多级内存阈值告警和自动清理
- **线程池监控**: 实时监控线程池状态和性能指标  
- **完整的管理API**: 健康检查、指标统计、会话管理

### 🚀 性能优化
- **批量Redis操作**: Pipeline批量处理减少网络开销
- **对象池管理**: 事件对象重用减少GC压力
- **智能拒绝策略**: 降级处理避免系统雪崩
- **JVM内置工具**: 高效获取系统指标

## 📖 快速开始

### 环境要求

- Java 8+
- Spring Boot 2.7+
- Redis (可选，用于分布式部署)
- Maven 3.6+

### 安装依赖

项目已配置完整的Maven依赖，只需要确保Redis服务运行即可。

### 启动应用

```bash
# 克隆项目
git clone <repository-url>

# 进入项目目录
cd websocket-framework

# 启动Redis (如果未运行)
redis-server

# 运行应用
mvn spring-boot:run
```

应用启动后访问以下地址：
- **测试页面**: http://localhost:8080/test.html
- **监控页面**: http://localhost:8080/monitor.html  
- **健康检查**: http://localhost:8080/websocket/admin/health

## 🏗️ 架构设计

### 整体架构图

```
                    ┌─────────────────┐
                    │   WebSocket     │
                    │   Client        │
                    └─────────┬───────┘
                              │
                              v
    ┌─────────────────────────────────────────────────────────┐
    │                WebSocketServer                          │
    │  ┌─────────────┬─────────────┬─────────────────────────┐│
    │  │   OnOpen    │  OnMessage  │      OnClose/OnError    ││
    │  └─────────────┴─────────────┴─────────────────────────┘│
    └─────────────────────┬───────────────────────────────────┘
                          │
                          v
    ┌─────────────────────────────────────────────────────────┐
    │              WebSocketEventBus                          │
    │  ┌─────────────┬─────────────┬─────────────────────────┐│
    │  │ Event Pub   │ Async Exec  │   Exception Handle      ││
    │  └─────────────┴─────────────┴─────────────────────────┘│
    └─────────────────────┬───────────────────────────────────┘
                          │
                          v
    ┌─────────────────────────────────────────────────────────┐
    │         WebSocketMessageHandlerDispatcher               │
    │  ┌─────────────┬─────────────┬─────────────────────────┐│
    │  │Interceptor  │  Handler    │    Priority Sort        ││
    │  │   Chain     │  Dispatch   │                         ││
    │  └─────────────┴─────────────┴─────────────────────────┘│
    └─────────┬──────────────────────┬────────────────────────┘
              │                      │
              v                      v
    ┌──────────────────┐    ┌──────────────────────────────────┐
    │ SessionManager   │    │     Custom Handlers              │
    │ ┌──────────────┐ │    │ ┌──────────────┬──────────────┐  │
    │ │   Local      │ │    │ │  ChatRoom    │ Notification │  │
    │ │   Cache      │ │    │ │   Service    │   Service    │  │
    │ └──────────────┘ │    │ └──────────────┴──────────────┘  │
    │ ┌──────────────┐ │    └──────────────────────────────────┘
    │ │    Redis     │ │
    │ │Distributed   │ │
    │ │   Cache      │ │
    │ └──────────────┘ │
    └──────────────────┘
```

### 核心组件结构

```
📁 websocket-framework/
├── 🏗️ core/                     # 核心组件
│   ├── WebSocketServer           # 主要端点 - 处理连接生命周期  
│   ├── WebSocketConstants        # 框架常量定义
│   └── WebSocketEndpointConfig   # 端点配置器
├── 🔄 session/                   # 会话管理
│   ├── WebSocketSessionManager   # 会话管理器 - 三层映射结构
│   ├── WebSocketSession          # 会话包装类 - 增强原生Session
│   ├── WebSocketSessionCleaner   # 自动清理器 - 内存泄漏预防
│   └── SessionStatistics         # 会话统计信息
├── 📡 event/                     # 事件系统  
│   ├── WebSocketEventBus         # 事件总线 - 基于Guava EventBus
│   ├── WebSocketEvent            # 事件对象 - 支持对象池
│   └── WebSocketEventType        # 事件类型枚举
├── 🎯 handler/                   # 消息处理
│   ├── WebSocketMessageHandler   # 处理器接口
│   ├── WebSocketMessageHandlerDispatcher # 调度器 - 支持拦截器链
│   └── DefaultWebSocketMessageHandler # 默认处理器
├── 🔍 interceptor/               # 拦截器
│   ├── WebSocketEventInterceptor # 拦截器接口 - AOP思想
│   └── LoggingWebSocketEventInterceptor # 日志拦截器
├── 📊 monitor/                   # 监控组件
│   ├── ThreadPoolMonitor         # 自适应线程池监控
│   ├── ThreadPoolMetricsStore    # 指标存储
│   ├── ThreadPoolHealthChecker   # 健康检查器
│   └── MonitorViewController     # 监控页面控制器
├── ⚙️ config/                    # 配置管理
│   ├── WebSocketFrameworkConfig  # 自动配置类
│   └── WebSocketFrameworkProperties # 配置属性类
├── 🔧 util/                      # 工具类
│   └── TimeoutTaskWrapper        # 超时任务包装器
├── 🏷️ annotation/                # 注解支持
│   ├── WebSocketService          # 服务注解
│   └── WebSocketEventListener    # 监听器注解
└── 💡 example/                   # 示例代码
    ├── ChatRoomWebSocketService  # 聊天室示例
    ├── NotificationWebSocketService # 通知推送示例
    └── WebSocketTestController   # 测试控制器
```

## 💻 快速上手

### 🚀 创建WebSocket服务

```java
@Component
@WebSocketService(value = "myservice", name = "我的服务")
public class MyWebSocketService implements WebSocketMessageHandler<String> {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Override
    public Object handleEvent(WebSocketEvent<String> event) {
        switch (event.getEventType()) {
            case ON_OPEN:
                // 处理连接建立
                handleUserConnect(event);
                break;
            case ON_MESSAGE:
                // 处理消息接收
                handleMessage(event);
                break;
            case ON_CLOSE:
                // 处理连接关闭
                handleUserDisconnect(event);
                break;
        }
        return null;
    }

    @Override
    public String[] getSupportedServices() {
        return new String[]{"myservice"};
    }

    private void handleUserConnect(WebSocketEvent<String> event) {
        String userId = event.getUserId();
        sessionManager.sendMessage("myservice", userId, "欢迎连接!");
    }

    private void handleMessage(WebSocketEvent<String> event) {
        String message = event.getData();
        // 处理用户消息
        sessionManager.broadcast("myservice", "用户消息: " + message);
    }

    private void handleUserDisconnect(WebSocketEvent<String> event) {
        // 处理用户断开连接
        System.out.println("用户断开连接: " + event.getUserId());
    }
}
```

### 📱 客户端连接

```javascript
// 连接WebSocket
const ws = new WebSocket('ws://localhost:8080/websocket/connect/myservice/user123');

ws.onopen = function(event) {
    console.log('连接成功');
};

ws.onmessage = function(event) {
    console.log('收到消息:', event.data);
};

ws.onclose = function(event) {
    console.log('连接关闭');
};

// 发送消息
ws.send('Hello WebSocket!');
```

### 💬 发送消息

```java
@Autowired
private WebSocketSessionManager sessionManager;

// 发送消息给指定用户
sessionManager.sendMessage("myservice", "user123", "Hello User!");

// 广播消息给所有用户
sessionManager.broadcast("myservice", "Hello Everyone!");

// 检查用户是否在线
boolean online = sessionManager.isOnline("myservice", "user123");
```

### 🎧 自定义事件监听器

```java
@Component
public class MyEventListener {

    @Subscribe
    public void handleWebSocketEvent(WebSocketEvent<?> event) {
        // 处理所有WebSocket事件
        System.out.println("收到事件: " + event.getEventType());
    }
}

// 注册监听器
@Autowired
private WebSocketEventBus eventBus;

@PostConstruct
public void init() {
    eventBus.register(new MyEventListener());
}
```

## ⚙️ 配置详解

### 📋 完整配置示例

```yaml
# WebSocket框架完整配置
websocket:
  framework:
    # 心跳配置
    heartbeat:
      interval: 30              # 心跳间隔（秒）
      timeout: 60               # 心跳超时（秒）
    
    # 线程池配置
    thread-pool:
      core-size: 20             # 核心线程数
      max-size: 100             # 最大线程数  
      queue-capacity: 1000      # 队列容量
      keep-alive: 60            # 线程保活时间（秒）
      task-timeout: 300         # 任务执行超时时间（秒）
      queue-warning-threshold: 1000   # 队列警告阈值
      queue-danger-threshold: 5000    # 队列危险阈值
      
      # 智能监控配置
      monitoring:
        enabled: true           # 启用智能监控
        initial-interval: 30    # 初始监控间隔（秒）
        min-interval: 5         # 最小监控间隔（秒）
        max-interval: 120       # 最大监控间隔（秒）
        initial-sampling-rate: 2 # 初始采样率
        health-thresholds:
          pool-utilization-warning: 0.7    # 线程池利用率警告阈值
          pool-utilization-critical: 0.9   # 线程池利用率严重阈值
          queue-utilization-warning: 0.5   # 队列利用率警告阈值
          queue-utilization-critical: 0.8  # 队列利用率严重阈值
          rejection-rate-warning: 0.01     # 任务拒绝率警告阈值
          rejection-rate-critical: 0.05    # 任务拒绝率严重阈值
    
    # 会话配置
    session:
      max-idle-time: 300        # 最大空闲时间（秒）
      cleanup-interval: 60      # 清理间隔（秒）
    
    # 消息配置
    message:
      max-size: 1048576         # 最大消息大小（字节，1MB）
      buffer-size: 8192         # 缓冲区大小（8KB）
    
    # 功能开关
    features:
      metrics: true             # 启用指标统计
      health-check: true        # 启用健康检查
      admin-api: true           # 启用管理API
      redis-enabled: true       # 启用Redis功能
      monitoring-ui: true       # 启用监控界面

# Spring Boot配置
spring:
  # Redis分布式缓存配置
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: ${REDIS_DATABASE:10}
    timeout: 5000ms
    jedis:
      pool:
        max-active: 800         # 最大连接数
        max-wait: 3000          # 最大等待时间
        max-idle: 20            # 最大空闲连接
        min-idle: 5             # 最小空闲连接

# 日志配置
logging:
  level:
    com.framework.websocket: INFO
    com.framework.websocket.monitor: INFO
    root: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/websocket-framework.log
    max-size: 100MB
    max-history: 30
```

### 🔒 环境变量支持

生产环境推荐使用环境变量配置敏感信息：

```bash
# Redis配置
export REDIS_HOST=your-redis-host
export REDIS_PORT=6379
export REDIS_PASSWORD=your-redis-password
export REDIS_DATABASE=10

# 应用配置  
export SERVER_PORT=8080
export LOG_LEVEL=WARN
```

## 🎪 示例演示

### 💬 聊天室示例

**功能特性**:
- ✅ 用户加入/离开实时通知
- ✅ 消息实时广播
- ✅ 在线用户统计和展示
- ✅ 私聊功能支持
- ✅ 消息历史记录

**连接方式**: `ws://localhost:8080/websocket/connect/chatroom/{userId}`

**测试步骤**:
1. 访问 http://localhost:8080/test.html
2. 选择"聊天室"功能
3. 输入用户ID连接
4. 发送消息测试广播功能

### 🔔 通知推送示例

**功能特性**:
- ✅ 个人通知精准推送
- ✅ 广播通知全员推送
- ✅ 定时推送任务
- ✅ 系统维护通知
- ✅ 推送状态跟踪

**连接方式**: `ws://localhost:8080/websocket/connect/notification/{userId}`

**测试步骤**:
1. 访问 http://localhost:8080/test.html
2. 选择"通知推送"功能
3. 输入用户ID连接
4. 通过管理API发送通知测试

## 📊 运维监控

### 🔍 实时监控面板

访问 http://localhost:8080/monitor.html 查看实时监控面板：

- **📈 连接状态监控**: 实时连接数、历史趋势图
- **⚡ 性能指标监控**: 消息吞吐量、响应时间、错误率
- **🧠 线程池监控**: 线程池状态、队列使用情况、健康评分
- **💾 内存监控**: 内存使用率、GC情况、会话内存占用
- **📋 会话管理**: 在线用户列表、会话详情、强制断开

### 🛠️ 管理API接口

框架提供了完整的管理API用于运维监控：

#### 健康检查
```bash
# 获取健康状态
GET /websocket/admin/health

# 响应示例
{
  "status": "UP",
  "totalConnections": 25,
  "frameworkVersion": "1.0.0",
  "details": {
    "redis": "UP",
    "eventBus": "UP"
  }
}
```

#### 指标监控
```bash
# 获取系统指标
GET /websocket/admin/metrics

# 响应示例
{
  "currentConnections": 25,
  "totalConnections": 156,
  "totalDisconnections": 131,
  "totalMessagesReceived": 2456,
  "totalMessagesSent": 2389,
  "totalErrors": 3,
  "totalHeartbeatTimeouts": 12
}
```

#### 会话管理
```bash
# 获取在线用户列表
GET /websocket/admin/sessions/{service}/users

# 获取服务连接数
GET /websocket/admin/sessions/{service}/count

# 发送消息给指定用户
POST /websocket/admin/sessions/{service}/{userId}/send
{
  "message": "Hello from admin!"
}

# 广播消息
POST /websocket/admin/sessions/{service}/broadcast
{
  "message": "System notification to all users"
}

# 获取配置信息
GET /websocket/admin/config
```

### 📊 性能监控与告警

#### 内置监控指标

**连接指标**:
- `websocket.connections.current` - 当前连接数
- `websocket.connections.total` - 累计连接数  
- `websocket.connections.peak` - 峰值连接数

**消息指标**:
- `websocket.messages.received` - 接收消息总数
- `websocket.messages.sent` - 发送消息总数
- `websocket.messages.processing_time` - 消息处理时长

**线程池指标**:
- `websocket.threadpool.active` - 活跃线程数
- `websocket.threadpool.queue_size` - 队列大小
- `websocket.threadpool.rejection_rate` - 任务拒绝率

#### 告警策略

系统内置智能告警机制：

```java
// 自动健康状态评估
ThreadPoolHealthStatus status = threadPoolMonitor.analyzeHealthStatus();

// 多级告警阈值
HEALTHY    - 健康状态，正常运行
WARNING    - 预警状态，资源使用率 > 70%
CRITICAL   - 严重状态，资源使用率 > 90%  
EMERGENCY  - 紧急状态，系统濒临崩溃
```

#### 集成外部监控

支持集成Prometheus、Grafana等监控系统：

```java
@Component
public class PrometheusMetricsExporter {
    
    @EventListener
    public void exportMetrics(ThreadPoolMetrics metrics) {
        Metrics.gauge("websocket_connections_current", metrics.getActiveConnections());
        Metrics.counter("websocket_messages_total", metrics.getTotalMessages());
        Metrics.timer("websocket_message_processing_time", metrics.getProcessingTime());
    }
}
```

### 🔧 故障排查

#### 常见问题诊断

**连接问题**:
```bash
# 检查连接状态
curl http://localhost:8080/websocket/admin/health

# 查看连接详情  
curl http://localhost:8080/websocket/admin/sessions/chatroom/users
```

**性能问题**:
```bash
# 查看线程池状态
curl http://localhost:8080/websocket/admin/metrics

# 触发手动GC（紧急情况）
curl -X POST http://localhost:8080/websocket/admin/gc
```

**内存问题**:
```bash
# 查看内存使用情况
curl http://localhost:8080/websocket/admin/memory

# 手动清理会话
curl -X POST http://localhost:8080/websocket/admin/cleanup
```

## 🔬 测试与验证

### 🧪 功能测试

访问测试页面进行完整功能验证：

- **基础测试**: http://localhost:8080/test.html
  - ✅ 聊天室功能完整测试
  - ✅ 通知推送功能测试  
  - ✅ 连接状态实时监控
  - ✅ 消息收发压力测试

- **监控测试**: http://localhost:8080/monitor.html
  - ✅ 实时监控面板验证
  - ✅ 性能指标图表展示
  - ✅ 告警机制测试
  - ✅ 管理操作验证

### 🎯 运维验证

通过管理API进行运维功能验证：

```bash
# 健康检查验证
curl http://localhost:8080/websocket/admin/health

# 性能指标验证  
curl http://localhost:8080/websocket/admin/metrics

# 会话管理验证
curl http://localhost:8080/websocket/admin/sessions/chatroom/users

# 配置信息验证
curl http://localhost:8080/websocket/admin/config
```

### ⚡ 压力测试

使用内置的压力测试工具：

```bash
# 编译项目
mvn clean compile

# 运行压力测试
mvn exec:java -Dexec.mainClass="com.framework.websocket.test.WebSocketStressTest" \
  -Dexec.args="--connections=1000 --duration=60 --message-rate=10"
```

**测试指标**:
- 支持1000+并发连接
- 消息处理延迟 < 10ms  
- 内存使用增长线性可控
- 无内存泄漏和连接泄漏

## 🔍 API文档

### WebSocketSessionManager

主要的会话管理接口：

```java
// 添加会话
void addSession(String service, String userId, WebSocketSession session)

// 移除会话
boolean removeSession(String service, String userId, WebSocketSession session)

// 获取会话
WebSocketSession getSession(String service, String userId)

// 发送消息
boolean sendMessage(String service, String userId, String message)

// 广播消息
void broadcast(String service, String message)

// 检查在线状态
boolean isOnline(String service, String userId)

// 获取在线用户数
int getOnlineCount(String service)
```

### WebSocketEventBus

事件总线接口：

```java
// 发布事件
void post(Object event)

// 注册监听器
void register(Object subscriber)

// 取消注册
void unregister(Object subscriber)
```

### WebSocketMetricsCollector

指标收集器接口：

```java
// 获取指标快照
MetricsSnapshot getMetricsSnapshot()

// 指标数据包含
class MetricsSnapshot {
    int currentConnections;        // 当前连接数
    long totalConnections;         // 总连接数
    long totalDisconnections;      // 总断开数
    long totalMessagesReceived;    // 总接收消息数
    long totalMessagesSent;        // 总发送消息数
    long totalErrors;              // 总错误数
    long totalHeartbeatTimeouts;   // 心跳超时数
}
```

### WebSocketAdminController

管理API接口：

```java
// 健康检查
GET /websocket/admin/health

// 获取指标
GET /websocket/admin/metrics

// 获取在线用户
GET /websocket/admin/sessions/{service}/users

// 获取连接数
GET /websocket/admin/sessions/{service}/count

// 发送消息
POST /websocket/admin/sessions/{service}/{userId}/send

// 广播消息
POST /websocket/admin/sessions/{service}/broadcast

// 获取配置
GET /websocket/admin/config
```

## 📚 学习资源

### 📖 项目文档

- **📋 [架构优化分析报告](docs/架构优化分析报告.md)** - 详细的架构分析和优化建议
- **🎓 [项目学习指南](docs/项目学习指南.md)** - 完整的学习路径和技术要点
- **🔧 [WebSocket并发安全详解](docs/WebSocket并发安全详解.md)** - 并发安全设计解析

### 💡 学习要点

通过学习本项目，你将掌握：

#### 🏗️ **架构设计**
- 事件驱动架构的企业级实现
- 多层级会话管理设计模式
- 拦截器链和责任链模式应用
- 分布式系统的会话一致性处理

#### 🚀 **性能优化**  
- 自定义线程池和任务超时控制
- Redis批量操作和Pipeline优化
- JVM内置工具的高效使用
- 内存泄漏预防和对象池管理

#### 📊 **监控运维**
- 自适应监控系统设计
- 多维度健康状态评估
- 智能告警和故障自愈机制
- 生产环境监控最佳实践

#### 🔒 **并发安全**
- 线程安全数据结构设计
- CAS操作和双重检查锁定
- 分布式锁和缓存一致性
- 高并发场景下的资源管理

### 🎯 适合人群

- **初级开发者**: 学习企业级WebSocket应用开发
- **中级开发者**: 提升架构设计和性能优化能力  
- **高级开发者**: 研究分布式系统和监控运维设计
- **架构师**: 参考企业级框架的设计思路

---

## 🛠️ 开发指南

### 🔧 自定义扩展开发

#### 消息处理器扩展

```java
@Component
public class CustomMessageHandler implements WebSocketMessageHandler<String> {
    
    @Override
    public Object handleEvent(WebSocketEvent<String> event) {
        // 自定义处理逻辑
        return null;
    }
    
    @Override
    public String[] getSupportedServices() {
        return new String[]{"custom"};
    }
    
    @Override
    public int getPriority() {
        return 100; // 设置优先级
    }
}
```

#### 事件拦截器扩展

```java
@Component
public class CustomInterceptor implements WebSocketEventInterceptor {
    
    @Override
    public boolean preHandle(WebSocketEvent<?> event) {
        // 前置处理
        return true;
    }
    
    @Override
    public void postHandle(WebSocketEvent<?> event, Object result) {
        // 后置处理
    }
    
    @Override
    public void afterCompletion(WebSocketEvent<?> event, Object result, Exception ex) {
        // 完成后处理
    }
}
```

### ⚠️ 生产部署注意事项

#### 🔒 安全配置
- **环境变量**: 生产环境必须使用环境变量配置敏感信息
- **访问控制**: 对管理API端点进行IP白名单或认证控制
- **HTTPS**: 生产环境建议使用WSS协议（WebSocket over SSL）
- **防火墙**: 配置适当的防火墙规则限制访问

#### 🚀 性能调优
- **JVM参数**: 根据并发量调整堆内存和GC策略
- **Redis配置**: 优化Redis连接池和持久化配置
- **线程池**: 根据业务特点调整线程池参数
- **监控告警**: 集成APM工具进行全方位监控

#### 📊 运维监控
- **日志收集**: 配置日志收集和分析系统
- **指标监控**: 集成Prometheus+Grafana监控体系  
- **健康检查**: 配置负载均衡器健康检查
- **备份策略**: 制定Redis数据备份和恢复策略

#### 🔧 故障处理
- **降级策略**: 准备Redis不可用时的降级方案
- **限流熔断**: 实现客户端限流和服务熔断机制
- **异常恢复**: 建立完善的异常恢复和通知机制
- **灾备方案**: 制定跨机房/云的灾备切换方案

---

## 🤝 贡献指南

### 📋 开发流程

1. **Fork项目** 并创建特性分支
2. **编写代码** 遵循项目代码风格
3. **添加测试** 确保测试覆盖率
4. **更新文档** 包括API文档和README
5. **提交PR** 详细描述变更内容

### 🔍 代码规范

- 遵循Google Java Style Guide
- 类和方法必须有完整的JavaDoc注释
- 单元测试覆盖率不低于80%
- 所有公共API必须向后兼容

### 🐛 问题反馈

遇到问题请提供以下信息：
- Java版本和操作系统
- 完整的错误日志和堆栈跟踪
- 复现步骤和最小化示例代码
- 相关配置信息（脱敏后）

---

## 📄 许可证

本项目采用 **MIT License** 开源协议，详见 [LICENSE](LICENSE) 文件。

## 🙏 致谢

感谢以下开源项目的支持：
- [Spring Boot](https://spring.io/projects/spring-boot) - 企业级应用框架
- [Google Guava](https://github.com/google/guava) - Java核心库扩展
- [Redis](https://redis.io/) - 高性能缓存数据库
- [Hutool](https://hutool.cn/) - Java工具包

---

## 📞 技术支持

### 📚 文档资源
- **官方文档**: 查看docs目录下的详细文档
- **API参考**: 查看代码中的JavaDoc注释
- **示例代码**: 参考example包中的实现

### 💬 社区交流
- **GitHub Issues**: 提交Bug报告和功能请求
- **Pull Requests**: 贡献代码和文档改进
- **技术博客**: 分享使用经验和最佳实践

### 🚀 企业支持
如需企业级技术支持、定制开发或培训服务，请通过GitHub Issues联系。

---

<div align="center">

## 🌟 如果这个项目对你有帮助，请给个Star支持一下！

**WebSocket框架 v1.0.0** - 功能完整、监控友好、生产就绪！

[![GitHub stars](https://img.shields.io/github/stars/your-username/websocket-framework.svg?style=social&label=Star)](https://github.com/your-username/websocket-framework)
[![GitHub forks](https://img.shields.io/github/forks/your-username/websocket-framework.svg?style=social&label=Fork)](https://github.com/your-username/websocket-framework)

*最后更新: 2025-08-01*

</div>