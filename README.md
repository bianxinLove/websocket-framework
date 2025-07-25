# WebSocket框架

基于Spring Boot的WebSocket框架，提供了完整的WebSocket连接管理、事件处理、会话管理、心跳检测等功能。

## 🚀 特性

- **完整的会话管理**: 支持多层级会话管理，自动维护用户连接状态
- **事件驱动架构**: 基于Google Guava EventBus的异步事件处理机制
- **心跳检测**: 自动心跳检测和连接保活功能
- **Redis集成**: 支持分布式会话管理和状态持久化
- **灵活的消息处理**: 支持自定义消息处理器和拦截器
- **注解驱动**: 提供便捷的注解支持，简化开发
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
│   └── SessionStatistics    # 会话统计
├── event/                   # 事件处理
│   ├── WebSocketEventBus    # 事件总线
│   ├── WebSocketEvent       # 事件对象
│   └── WebSocketEventType   # 事件类型
├── handler/                 # 消息处理
│   ├── WebSocketMessageHandler # 处理器接口
│   └── DefaultWebSocketMessageHandler # 默认处理器
├── interceptor/            # 拦截器
│   ├── WebSocketEventInterceptor # 拦截器接口
│   └── LoggingWebSocketEventInterceptor # 日志拦截器
├── annotation/             # 注解
│   ├── WebSocketService    # 服务注解
│   └── WebSocketEventListener # 监听器注解
├── config/                 # 配置
│   └── WebSocketFrameworkConfig # 框架配置
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
    
    # 会话配置
    session:
      max-idle-time: 300  # 最大空闲时间（秒）
      cleanup-interval: 60  # 清理间隔（秒）
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

## 🧪 测试

访问 http://localhost:8080/test.html 进行功能测试，页面提供了：
- 聊天室功能测试
- 通知推送功能测试
- 连接状态监控
- 消息收发测试

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

## 🚨 注意事项

1. **Redis依赖**: 分布式部署时需要Redis支持，单机部署可以不使用Redis
2. **心跳机制**: 默认启用心跳检测，可通过配置调整心跳间隔
3. **线程安全**: 所有核心组件都是线程安全的
4. **内存管理**: 长期运行时注意会话清理，避免内存泄漏
5. **错误处理**: 建议在业务代码中添加适当的异常处理

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

框架内置了会话统计功能，可以获取：
- 连接数统计
- 消息收发统计
- 连接持续时间
- 心跳状态监控

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

**WebSocket框架 v1.0.0** - 让WebSocket开发更简单！