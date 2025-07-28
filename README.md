# WebSocket框架

基于Spring Boot的WebSocket框架，提供了完整的WebSocket连接管理、事件处理、会话管理、心跳检测等功能。

## 🚀 特性

- **完整的会话管理**: 支持多层级会话管理，自动维护用户连接状态
- **事件驱动架构**: 基于Google Guava EventBus的异步事件处理机制
- **心跳检测**: 自动心跳检测和连接保活功能
- **Redis集成**: 支持分布式会话管理和状态持久化
- **灵活的消息处理**: 支持自定义消息处理器和拦截器
- **注解驱动**: 提供便捷的注解支持，简化开发
- **指标监控**: 实时连接数、消息统计、错误监控
- **会话清理**: 自动清理断开连接和超时会话
- **管理API**: RESTful API支持运维管理和监控
- **健康检查**: 内置健康检查端点
- **配置化管理**: 支持外部化配置和环境变量
- **丰富的示例**: 包含聊天室和通知推送等实用示例
- **可扩展设计**: 支持自定义扩展和插件化开发

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

应用启动后，访问 http://localhost:8080/test.html 进行测试。

## 🏗️ 架构设计

### 核心组件

```
websocket-framework/
├── core/                    # 核心组件
│   ├── WebSocketServer      # WebSocket服务器
│   ├── WebSocketConstants   # 常量定义
│   └── WebSocketEndpointConfig # 端点配置
├── session/                 # 会话管理
│   ├── WebSocketSessionManager  # 会话管理器
│   ├── WebSocketSession     # 会话包装类
│   ├── WebSocketSessionCleaner  # 会话清理器
│   └── SessionStatistics    # 会话统计
├── event/                   # 事件处理
│   ├── WebSocketEventBus    # 事件总线
│   ├── WebSocketEvent       # 事件对象
│   └── WebSocketEventType   # 事件类型
├── handler/                 # 消息处理
│   ├── WebSocketMessageHandler # 处理器接口
│   ├── WebSocketMessageHandlerDispatcher # 处理器调度器
│   └── DefaultWebSocketMessageHandler # 默认处理器
├── interceptor/            # 拦截器
│   ├── WebSocketEventInterceptor # 拦截器接口
│   └── LoggingWebSocketEventInterceptor # 日志拦截器
├── annotation/             # 注解
│   ├── WebSocketService    # 服务注解
│   └── WebSocketEventListener # 监听器注解
├── config/                 # 配置
│   ├── WebSocketFrameworkConfig # 框架配置
│   └── WebSocketFrameworkProperties # 配置属性
├── metrics/                # 指标监控
│   └── WebSocketMetricsCollector # 指标收集器
├── admin/                  # 管理API
│   └── WebSocketAdminController # 管理控制器
└── example/               # 示例代码
    ├── ChatRoomWebSocketService # 聊天室示例
    └── NotificationWebSocketService # 通知推送示例
```

## 💻 使用指南

### 1. 创建WebSocket服务

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

### 2. 客户端连接

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

### 3. 发送消息

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

### 4. 自定义事件监听器

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

## 🔧 配置选项

### application.yml配置

```yaml
websocket:
  framework:
    # 心跳配置
    heartbeat:
      interval: 30  # 心跳间隔（秒）
      timeout: 60   # 心跳超时（秒）
    
    # 线程池配置
    thread-pool:
      core-size: 10
      max-size: 50
      queue-capacity: 1000
      keep-alive: 60
    
    # 会话配置
    session:
      max-idle-time: 300  # 最大空闲时间（秒）
      cleanup-interval: 60  # 清理间隔（秒）
    
    # 消息配置
    message:
      max-size: 1048576  # 最大消息大小（字节）
      buffer-size: 8192   # 缓冲区大小
    
    # 功能开关
    features:
      metrics: true       # 启用指标统计
      health-check: true  # 启用健康检查
      admin-api: true     # 启用管理API

# Redis配置（支持环境变量）
spring:
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:123456}  # 支持环境变量
    database: 10
```

## 📝 示例说明

### 聊天室示例

提供了完整的聊天室功能：
- 用户加入/离开通知
- 实时消息广播
- 在线用户统计
- 私聊功能

连接地址: `ws://localhost:8080/websocket/connect/chatroom/{userId}`

### 通知推送示例

提供了通知推送功能：
- 个人通知推送
- 广播通知
- 定时推送
- 系统维护通知

连接地址: `ws://localhost:8080/websocket/connect/notification/{userId}`

## 📊 运维监控

### 管理API

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

### 自动化监控

框架内置了自动化监控功能：

- **会话清理**: 自动检测并清理断开的连接和超时会话
- **指标收集**: 实时收集连接数、消息数、错误数等关键指标
- **健康检查**: 定期检查系统健康状态
- **心跳监控**: 监控客户端心跳状态，及时发现异常连接

## 🧪 测试

访问 http://localhost:8080/test.html 进行功能测试，页面提供了：
- 聊天室功能测试
- 通知推送功能测试
- 连接状态监控
- 消息收发测试

### 运维测试

访问管理API进行运维功能测试：
- 健康检查: http://localhost:8080/websocket/admin/health
- 指标监控: http://localhost:8080/websocket/admin/metrics
- 在线用户: http://localhost:8080/websocket/admin/sessions/chatroom/users

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

## 🚨 注意事项

1. **Redis依赖**: 分布式部署时需要Redis支持，单机部署可以不使用Redis
2. **心跳机制**: 默认启用心跳检测，可通过配置调整心跳间隔
3. **线程安全**: 所有核心组件都是线程安全的
4. **内存管理**: 框架内置自动会话清理，防止内存泄漏
5. **错误处理**: 建议在业务代码中添加适当的异常处理
6. **安全配置**: 生产环境请使用环境变量配置敏感信息
7. **管理API**: 生产环境建议对管理API进行访问控制
8. **指标监控**: 可集成到监控系统（如Prometheus）进行可视化监控

## 🛠️ 扩展开发

### 自定义消息处理器

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

### 自定义拦截器

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

## 📊 性能监控

### 内置指标

框架提供了丰富的内置指标监控：

- **连接指标**: 当前连接数、总连接数、总断开数
- **消息指标**: 消息收发统计、消息处理时长
- **错误指标**: 连接错误、处理异常、心跳超时
- **性能指标**: 线程池状态、内存使用情况

### 集成监控系统

可以轻松集成到现有监控系统：

```java
@Component
public class PrometheusMetricsExporter {
    
    @Autowired
    private WebSocketMetricsCollector metricsCollector;
    
    @Scheduled(fixedRate = 30000)
    public void exportMetrics() {
        MetricsSnapshot snapshot = metricsCollector.getMetricsSnapshot();
        // 导出到Prometheus或其他监控系统
        Metrics.gauge("websocket.connections.current", snapshot.getCurrentConnections());
        Metrics.counter("websocket.connections.total", snapshot.getTotalConnections());
        // ... 其他指标
    }
}
```

### 会话统计

```java
// 获取会话统计信息
SessionStatistics stats = webSocketSession.getStatistics();
System.out.println("发送消息数: " + stats.getSendMessageCount());
System.out.println("接收消息数: " + stats.getReceiveMessageCount());
System.out.println("连接时长: " + stats.getFormattedConnectionDuration());
```

## 🤝 贡献指南

欢迎提交Issue和Pull Request来帮助改进这个框架。

## 📄 许可证

本项目采用MIT许可证，详见LICENSE文件。

## 🆘 支持

如果您在使用过程中遇到问题，可以：
1. 查看本文档
2. 查看示例代码
3. 提交Issue
4. 参考测试页面的实现

---

**WebSocket框架 v1.0.0** - 功能完整、监控友好、生产就绪的WebSocket解决方案！

## 🆕 更新日志

### v1.0.0 (2024-01-XX)
- ✅ 新增指标监控系统
- ✅ 新增管理API和健康检查
- ✅ 新增自动会话清理机制  
- ✅ 新增配置属性管理
- ✅ 优化安全配置和环境变量支持
- ✅ 增强错误处理和日志记录
- ✅ 完善文档和示例代码