package com.framework.websocket.session;

import com.framework.websocket.core.WebSocketConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket会话包装类
 * 封装原生Session，提供更丰富的功能
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
@Slf4j
@Data
public class WebSocketSession {

    /**
     * 原生WebSocket会话
     */
    private final Session session;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * 服务标识
     */
    private final String service;

    /**
     * 客户端IP
     */
    private final String clientIp;

    /**
     * 连接时间
     */
    private final LocalDateTime connectTime;

    /**
     * 最后心跳时间
     */
    private final AtomicLong lastHeartbeatTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 发送消息计数
     */
    private final AtomicLong sendMessageCount = new AtomicLong(0);

    /**
     * 接收消息计数
     */
    private final AtomicLong receiveMessageCount = new AtomicLong(0);

    /**
     * 扩展属性
     */
    private final Map<String, Object> attributes;

    public WebSocketSession(Session session, String userId, String service) {
        this.session = session;
        this.userId = userId;
        this.service = service;
        this.connectTime = LocalDateTime.now();
        this.clientIp = (String) session.getUserProperties().get(WebSocketConstants.CLIENT_IP_KEY);
        this.attributes = session.getUserProperties();
    }

    /**
     * 获取会话ID
     */
    public String getSessionId() {
        return session.getId();
    }

    /**
     * 发送文本消息
     */
    public void sendMessage(String message) throws IOException {
        if (session.isOpen()) {
            session.getBasicRemote().sendText(message);
            sendMessageCount.incrementAndGet();
            log.debug("消息发送成功: sessionId={}, userId={}, service={}, message={}", 
                    getSessionId(), userId, service, message);
        } else {
            log.warn("会话已关闭，消息发送失败: sessionId={}, userId={}, service={}", 
                    getSessionId(), userId, service);
            throw new IOException("WebSocket会话已关闭");
        }
    }

    /**
     * 发送二进制消息
     */
    public void sendBinary(ByteBuffer data) throws IOException {
        if (session.isOpen()) {
            session.getBasicRemote().sendBinary(data);
            sendMessageCount.incrementAndGet();
            log.debug("二进制消息发送成功: sessionId={}, userId={}, service={}", 
                    getSessionId(), userId, service);
        } else {
            log.warn("会话已关闭，二进制消息发送失败: sessionId={}, userId={}, service={}", 
                    getSessionId(), userId, service);
            throw new IOException("WebSocket会话已关闭");
        }
    }

    /**
     * 发送Ping消息（心跳）
     */
    public void sendPing() throws IOException {
        sendPing(ByteBuffer.allocate(0));
    }

    /**
     * 发送Ping消息（心跳）
     */
    public void sendPing(ByteBuffer data) throws IOException {
        if (session.isOpen()) {
            session.getBasicRemote().sendPing(data);
            log.debug("心跳消息发送成功: sessionId={}, userId={}, service={}", 
                    getSessionId(), userId, service);
        } else {
            log.warn("会话已关闭，心跳消息发送失败: sessionId={}, userId={}, service={}", 
                    getSessionId(), userId, service);
            throw new IOException("WebSocket会话已关闭");
        }
    }

    /**
     * 关闭会话
     */
    public void close() throws IOException {
        close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "正常关闭"));
    }

    /**
     * 关闭会话（指定关闭原因）
     */
    public void close(CloseReason closeReason) throws IOException {
        if (session.isOpen()) {
            session.close(closeReason);
            log.info("会话已关闭: sessionId={}, userId={}, service={}, reason={}", 
                    getSessionId(), userId, service, closeReason.getReasonPhrase());
        }
    }

    /**
     * 检查会话是否打开
     */
    public boolean isOpen() {
        return session.isOpen();
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat() {
        lastHeartbeatTime.set(System.currentTimeMillis());
    }

    /**
     * 检查心跳是否超时
     */
    public boolean isHeartbeatTimeout(long timeoutMillis) {
        return System.currentTimeMillis() - lastHeartbeatTime.get() > timeoutMillis;
    }

    /**
     * 增加接收消息计数
     */
    public void incrementReceiveCount() {
        receiveMessageCount.incrementAndGet();
    }

    /**
     * 获取会话属性
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 设置会话属性
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 移除会话属性
     */
    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }

    /**
     * 获取连接持续时间（毫秒）
     */
    public long getConnectionDuration() {
        return System.currentTimeMillis() - (connectTime != null ? 
                connectTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0);
    }

    /**
     * 获取会话统计信息
     */
    public SessionStatistics getStatistics() {
        return SessionStatistics.builder()
                .sessionId(getSessionId())
                .userId(userId)
                .service(service)
                .clientIp(clientIp)
                .connectTime(connectTime)
                .connectionDuration(getConnectionDuration())
                .sendMessageCount(sendMessageCount.get())
                .receiveMessageCount(receiveMessageCount.get())
                .lastHeartbeatTime(lastHeartbeatTime.get())
                .isOpen(isOpen())
                .build();
    }

    @Override
    public String toString() {
        return String.format("WebSocketSession{sessionId='%s', userId='%s', service='%s', clientIp='%s', isOpen=%s}", 
                getSessionId(), userId, service, clientIp, isOpen());
    }
}