# CLAUDE.md

必须用中文回复我

语法使用Java 8

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a WebSocket framework built on Spring Boot 2.7.0 using Java 8. It provides complete WebSocket connection management, event processing, session management, and heartbeat detection capabilities.

## Development Commands

### Build and Run
```bash
# Build the project
mvn clean compile

# Run the application
mvn spring-boot:run

# Package the application
mvn clean package

# Run tests
mvn test
```

### Testing
- Access test page: http://localhost:8080/test.html
- WebSocket endpoint pattern: `ws://localhost:8080/websocket/connect/{service}/{userId}`
- Example services: "chatroom", "notification"

## Architecture Overview

### Core Components

**WebSocketServer** (`core/WebSocketServer.java`): 
- Main WebSocket endpoint handling connection lifecycle
- Manages heartbeat detection with 30s intervals and 60s timeout
- Uses static dependency injection due to WebSocket endpoint instantiation requirements

**WebSocketSessionManager** (`session/WebSocketSessionManager.java`):
- Multi-level session management (service -> userId -> session)
- Redis integration for distributed session persistence
- Custom ConcurrentBiMap for thread-safe nested mapping

**WebSocketEventBus** (`event/WebSocketEventBus.java`):
- Asynchronous event distribution using Google Guava EventBus
- Custom exception handling for subscriber failures
- Thread pool managed event processing

### Key Design Patterns

1. **Event-Driven Architecture**: All WebSocket lifecycle events are published through the event bus for decoupled handling
2. **Session Hierarchy**: Three-tier session management (framework -> service -> user)
3. **Annotation-Based Services**: Use `@WebSocketService` and `@WebSocketEventListener` for service definition
4. **Redis Heartbeat Caching**: Heartbeat status stored in Redis with TTL for distributed deployments

### Configuration

Application configuration in `application.yml` supports:
- Custom heartbeat intervals and timeouts
- Thread pool sizing (core: 10, max: 50, queue: 1000)
- Session management parameters
- Message size limits and buffer configuration

### Dependencies

Key frameworks:
- Spring Boot 2.7.0 (WebSocket, Web, Data Redis)
- Google Guava EventBus for event handling
- Hutool utilities for common operations
- Lombok for boilerplate reduction

## Development Notes

### Creating WebSocket Services
Implement `WebSocketMessageHandler<T>` and annotate with `@WebSocketService(value="serviceName")`. The handler receives `WebSocketEvent` objects for all connection lifecycle events.

### Session Management
Use `WebSocketSessionManager` for sending messages, broadcasting, and checking online status. Sessions are automatically managed by the framework.

### Event Handling
Register event listeners using `@Subscribe` annotation and register with `WebSocketEventBus`. Events include: ON_OPEN, ON_MESSAGE, ON_CLOSE, ON_ERROR, ON_HEARTBEAT, ON_SEND.

### Redis Configuration
The project uses Redis for distributed session management. Update Redis configuration in `application.yml` for your environment.