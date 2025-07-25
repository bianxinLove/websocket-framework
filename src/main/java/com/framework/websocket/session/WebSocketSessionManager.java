package com.framework.websocket.session;

import com.framework.websocket.core.WebSocketConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket会话管理器
 * 支持多层级的会话管理和Redis持久化
 * 
 * @author WebSocket Framework
 * @version 1.0.0
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 会话存储池：service -> userId -> WebSocketSession
     */
    private final ConcurrentBiMap<String, String, WebSocketSession> sessionPool = new ConcurrentBiMap<>();

    @Autowired
    public WebSocketSessionManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加会话
     */
    public void addSession(String service, String userId, WebSocketSession session) {
        sessionPool.put(service, userId, session);
        updateHeartbeat(service, userId);
        log.info("WebSocket会话已添加: service={}, userId={}, sessionId={}", 
                service, userId, session.getSessionId());
    }

    /**
     * 移除会话
     */
    public boolean removeSession(String service, String userId, WebSocketSession session) {
        WebSocketSession cachedSession = sessionPool.get(service, userId);
        if (cachedSession != null && Objects.equals(cachedSession.getSessionId(), session.getSessionId())) {
            sessionPool.remove(service, userId);
            clearHeartbeat(service, userId);
            log.info("WebSocket会话已移除: service={}, userId={}, sessionId={}", 
                    service, userId, session.getSessionId());
            return true;
        } else {
            log.warn("WebSocket会话移除失败，会话不匹配: service={}, userId={}", service, userId);
            return false;
        }
    }

    /**
     * 移除并关闭会话
     */
    public void removeAndCloseSession(String service, String userId, WebSocketSession session) {
        boolean removed = removeSession(service, userId, session);
        if (removed) {
            try {
                session.close();
            } catch (IOException e) {
                log.error("关闭WebSocket会话失败: service={}, userId={}", service, userId, e);
            }
        }
    }

    /**
     * 获取会话
     */
    public WebSocketSession getSession(String service, String userId) {
        return sessionPool.get(service, userId);
    }

    /**
     * 获取指定服务的所有会话
     */
    public Map<String, WebSocketSession> getSessionsByService(String service) {
        return Optional.ofNullable(sessionPool.get(service)).orElse(Collections.emptyMap());
    }

    /**
     * 获取所有会话
     */
    public Map<String, Map<String, WebSocketSession>> getAllSessions() {
        return sessionPool.getAllSessions();
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline(String service, String userId) {
        String cacheKey = getHeartbeatCacheKey(service, userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey)) || sessionPool.get(service, userId) != null;
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat(String service, String userId) {
        updateHeartbeat(service, userId, WebSocketConstants.DEFAULT_HEARTBEAT_TIMEOUT);
    }

    /**
     * 更新心跳（指定超时时间）
     */
    public void updateHeartbeat(String service, String userId, int timeoutSeconds) {
        String cacheKey = getHeartbeatCacheKey(service, userId);
        redisTemplate.opsForValue().set(cacheKey, System.currentTimeMillis(), timeoutSeconds, TimeUnit.SECONDS);
        if (log.isDebugEnabled()) {
            log.debug("WebSocket心跳已更新: service={}, userId={}, timeout={}s", service, userId, timeoutSeconds);
        }
    }

    /**
     * 清除心跳记录
     */
    public void clearHeartbeat(String service, String userId) {
        String cacheKey = getHeartbeatCacheKey(service, userId);
        redisTemplate.delete(cacheKey);
    }

    /**
     * 广播消息给指定服务的所有用户
     */
    public void broadcast(String service, String message) {
        Map<String, WebSocketSession> sessions = getSessionsByService(service);
        sessions.forEach((userId, session) -> {
            try {
                session.sendMessage(message);
                log.debug("广播消息发送成功: service={}, userId={}, message={}", service, userId, message);
            } catch (Exception e) {
                log.error("广播消息发送失败: service={}, userId={}", service, userId, e);
            }
        });
        log.info("广播消息已发送: service={}, 接收用户数={}, message={}", service, sessions.size(), message);
    }

    /**
     * 发送消息给指定用户
     */
    public boolean sendMessage(String service, String userId, String message) {
        WebSocketSession session = getSession(service, userId);
        if (session != null) {
            try {
                session.sendMessage(message);
                log.info("消息发送成功: service={}, userId={}, message={}", service, userId, message);
                return true;
            } catch (Exception e) {
                log.error("消息发送失败: service={}, userId={}, message={}", service, userId, message, e);
            }
        } else {
            log.warn("消息发送失败，用户不在线: service={}, userId={}", service, userId);
        }
        return false;
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineCount(String service) {
        return getSessionsByService(service).size();
    }

    /**
     * 获取所有在线用户数
     */
    public int getTotalOnlineCount() {
        return sessionPool.size();
    }

    /**
     * 获取在线用户列表
     */
    public Set<String> getOnlineUsers(String service) {
        return getSessionsByService(service).keySet();
    }

    /**
     * 生成心跳缓存Key
     */
    private String getHeartbeatCacheKey(String service, String userId) {
        return WebSocketConstants.HEARTBEAT_CACHE_PREFIX + service + ":" + userId;
    }

    /**
     * 双层并发映射，支持 service -> userId -> session 的映射结构
     */
    private static class ConcurrentBiMap<K1, K2, V> {
        private final ConcurrentHashMap<K1, ConcurrentHashMap<K2, V>> innerMap = new ConcurrentHashMap<>();

        public void put(K1 key1, K2 key2, V value) {
            innerMap.computeIfAbsent(key1, k -> new ConcurrentHashMap<>()).put(key2, value);
        }

        public V get(K1 key1, K2 key2) {
            ConcurrentHashMap<K2, V> subMap = innerMap.get(key1);
            return (subMap != null) ? subMap.get(key2) : null;
        }

        public V remove(K1 key1, K2 key2) {
            ConcurrentHashMap<K2, V> subMap = innerMap.get(key1);
            if (subMap != null) {
                V removed = subMap.remove(key2);
                if (subMap.isEmpty()) {
                    innerMap.remove(key1);
                }
                return removed;
            }
            return null;
        }

        public Map<K2, V> get(K1 key1) {
            return innerMap.getOrDefault(key1, new ConcurrentHashMap<>());
        }

        public Map<K1, Map<K2, V>> getAllSessions() {
            Map<K1, Map<K2, V>> result = new HashMap<>();
            innerMap.forEach((k1, v) -> result.put(k1, new HashMap<>(v)));
            return result;
        }

        public int size() {
            return innerMap.values().stream().mapToInt(Map::size).sum();
        }
    }
}