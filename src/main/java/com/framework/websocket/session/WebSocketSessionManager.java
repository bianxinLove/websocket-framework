package com.framework.websocket.session;

import com.framework.websocket.core.WebSocketConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * WebSocket会话管理器
 * 支持多层级的会话管理和Redis持久化
 * 
 * @author bianxin
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
        // 首先检查本地会话池（更快）
        if (sessionPool.get(service, userId) != null) {
            return true;
        }
        
        // 如果本地没有，再检查Redis（分布式场景）
        String cacheKey = getHeartbeatCacheKey(service, userId);
        return redisTemplate.hasKey(cacheKey);
    }

    /**
     * 更新心跳（使用批量优化）
     */
    public void updateHeartbeat(String service, String userId) {
        updateHeartbeat(service, userId, WebSocketConstants.DEFAULT_HEARTBEAT_TIMEOUT);
    }

    /**
     * 更新心跳（指定超时时间，优化Redis操作）
     */
    public void updateHeartbeat(String service, String userId, int timeoutSeconds) {
        String cacheKey = getHeartbeatCacheKey(service, userId);
        
        // 使用Redis的SETEX命令，原子性设置值和TTL
        try {
            // 只存储时间戳，减少内存占用
            long currentTime = System.currentTimeMillis();
            redisTemplate.opsForValue().set(cacheKey, currentTime, timeoutSeconds, TimeUnit.SECONDS);
            
            if (log.isDebugEnabled()) {
                log.debug("WebSocket心跳已更新: service={}, userId={}, timeout={}s", service, userId, timeoutSeconds);
            }
        } catch (Exception e) {
            log.warn("更新心跳缓存失败: service={}, userId={}", service, userId, e);
        }
    }

    /**
     * 批量更新心跳（减少Redis网络开销）
     */
    public void batchUpdateHeartbeat(Map<String, Map<String, Integer>> serviceUserTimeouts) {
        if (serviceUserTimeouts.isEmpty()) {
            return;
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            
            // 使用Redis Pipeline批量执行
            redisTemplate.executePipelined((RedisCallback<?>) (connection) -> {
                for (Map.Entry<String, Map<String, Integer>> serviceEntry : serviceUserTimeouts.entrySet()) {
                    String service = serviceEntry.getKey();
                    for (Map.Entry<String, Integer> userEntry : serviceEntry.getValue().entrySet()) {
                        String userId = userEntry.getKey();
                        int timeoutSeconds = userEntry.getValue();
                        String cacheKey = getHeartbeatCacheKey(service, userId);
                        
                        // 批量设置
                        connection.setEx(cacheKey.getBytes(), timeoutSeconds, String.valueOf(currentTime).getBytes());
                    }
                }
                return null;
            });
            
            if (log.isDebugEnabled()) {
                int totalUpdates = serviceUserTimeouts.values().stream().mapToInt(Map::size).sum();
                log.debug("批量更新心跳完成，共{}个会话", totalUpdates);
            }
        } catch (Exception e) {
            log.error("批量更新心跳失败", e);
        }
    }

    /**
     * 清除心跳记录（支持批量删除）
     */
    public void clearHeartbeat(String service, String userId) {
        String cacheKey = getHeartbeatCacheKey(service, userId);
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("清除心跳缓存失败: service={}, userId={}", service, userId, e);
        }
    }

    /**
     * 批量清除心跳记录
     */
    public void batchClearHeartbeat(List<String> services, List<String> userIds) {
        if (services.size() != userIds.size()) {
            throw new IllegalArgumentException("服务列表和用户ID列表长度不匹配");
        }
        
        try {
            List<String> keysToDelete = new ArrayList<>();
            for (int i = 0; i < services.size(); i++) {
                String cacheKey = getHeartbeatCacheKey(services.get(i), userIds.get(i));
                keysToDelete.add(cacheKey);
            }
            
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.debug("批量清除心跳缓存完成，共{}个键", keysToDelete.size());
            }
        } catch (Exception e) {
            log.error("批量清除心跳缓存失败", e);
        }
    }

    /**
     * 清理过期的心跳缓存（定期维护）
     */
    public void cleanupExpiredHeartbeats() {
        try {
            String pattern = WebSocketConstants.HEARTBEAT_CACHE_PREFIX + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (!keys.isEmpty()) {
                int initialSize = keys.size();
                
                // 检查并删除已过期但Redis未自动清理的键
                List<String> expiredKeys = new ArrayList<>();
                for (String key : keys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl <= 0) {
                        expiredKeys.add(key);
                    }
                }
                
                if (!expiredKeys.isEmpty()) {
                    redisTemplate.delete(expiredKeys);
                    log.info("清理过期心跳缓存: 总键数={}, 清理键数={}", initialSize, expiredKeys.size());
                }
            }
        } catch (Exception e) {
            log.error("清理过期心跳缓存失败", e);
        }
    }

    /**
     * 广播消息给指定服务的所有用户
     */
    public void broadcast(String service, String message) {
        Map<String, WebSocketSession> sessions = getSessionsByService(service);
        List<String> failedUsers = new ArrayList<>();
        
        sessions.forEach((userId, session) -> {
            try {
                // 检查会话是否仍然有效
                if (session.isOpen()) {
                    session.sendMessage(message);
                    log.debug("广播消息发送成功: service={}, userId={}", service, userId);
                } else {
                    log.warn("会话已关闭，跳过发送: service={}, userId={}", service, userId);
                    failedUsers.add(userId);
                }
            } catch (Exception e) {
                log.error("广播消息发送失败: service={}, userId={}", service, userId, e);
                failedUsers.add(userId);
            }
        });
        
        // 清理失效的会话
        for (String userId : failedUsers) {
            removeSession(service, userId, sessions.get(userId));
            log.info("清理失效会话: service={}, userId={}", service, userId);
        }
        
        int successCount = sessions.size() - failedUsers.size();
        log.info("广播消息已发送: service={}, 成功用户数={}, 失败用户数={}", 
            service, successCount, failedUsers.size());
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
     * 获取服务数量
     */
    public int getServiceCount() {
        return sessionPool.getServiceCount();
    }

    /**
     * 获取在线用户列表
     */
    public Set<String> getOnlineUsers(String service) {
        return getSessionsByService(service).keySet();
    }

    /**
     * 强制清理空的服务Map，释放内存
     */
    public void forceCleanup() {
        sessionPool.forceCleanup();
        log.info("强制清理完成: {}", sessionPool.getMemoryStats());
    }

    /**
     * 获取内存使用统计信息
     */
    public String getMemoryStats() {
        return sessionPool.getMemoryStats();
    }

    /**
     * 生成心跳缓存Key
     */
    private String getHeartbeatCacheKey(String service, String userId) {
        return WebSocketConstants.HEARTBEAT_CACHE_PREFIX + service + ":" + userId;
    }

    /**
     * 增强的双层并发映射，支持 service -> userId -> session 的映射结构
     * 修复内存泄漏问题，增加自动清理机制
     */
    private static class ConcurrentBiMap<K1, K2, V> {
        private final ConcurrentHashMap<K1, ConcurrentHashMap<K2, V>> innerMap = new ConcurrentHashMap<>();
        private final AtomicLong operationCounter = new AtomicLong(0);
        private static final int CLEANUP_THRESHOLD = 1000; // 每1000次操作触发一次清理

        public void put(K1 key1, K2 key2, V value) {
            innerMap.computeIfAbsent(key1, k -> new ConcurrentHashMap<>()).put(key2, value);
            triggerPeriodicCleanup();
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
                    // 双重检查锁定模式确保线程安全
                    synchronized (innerMap) {
                        ConcurrentHashMap<K2, V> recheckedSubMap = innerMap.get(key1);
                        if (recheckedSubMap != null && recheckedSubMap.isEmpty()) {
                            innerMap.remove(key1);
                        }
                    }
                }
                triggerPeriodicCleanup();
                return removed;
            }
            return null;
        }

        public Map<K2, V> get(K1 key1) {
            ConcurrentHashMap<K2, V> subMap = innerMap.get(key1);
            return (subMap != null) ? new ConcurrentHashMap<>(subMap) : new ConcurrentHashMap<>();
        }

        public Map<K1, Map<K2, V>> getAllSessions() {
            Map<K1, Map<K2, V>> result = new HashMap<>();
            innerMap.forEach((k1, subMap) -> {
                if (!subMap.isEmpty()) {
                    result.put(k1, new HashMap<>(subMap));
                }
            });
            return result;
        }

        public int size() {
            return innerMap.values().stream().mapToInt(Map::size).sum();
        }

        /**
         * 获取服务数量
         */
        public int getServiceCount() {
            return innerMap.size();
        }

        /**
         * 清理空的子Map，防止内存泄漏
         */
        public void cleanup() {
            synchronized (innerMap) {
                Set<K1> emptyKeys = innerMap.entrySet().stream()
                        .filter(entry -> entry.getValue().isEmpty())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
                
                if (!emptyKeys.isEmpty()) {
                    emptyKeys.forEach(innerMap::remove);
                    if (log.isDebugEnabled()) {
                        log.debug("清理了{}个空的服务Map", emptyKeys.size());
                    }
                }
            }
        }

        /**
         * 定期触发清理操作
         */
        private void triggerPeriodicCleanup() {
            if (operationCounter.incrementAndGet() % CLEANUP_THRESHOLD == 0) {
                cleanup();
            }
        }

        /**
         * 强制清理所有空的子Map
         */
        public void forceCleanup() {
            cleanup();
        }

        /**
         * 获取内存使用统计信息
         */
        public String getMemoryStats() {
            int serviceCount = innerMap.size();
            int totalSessions = size();
            int emptyServices = (int) innerMap.values().stream().filter(Map::isEmpty).count();
            
            return String.format("Services: %d (Empty: %d), Sessions: %d, Operations: %d", 
                    serviceCount, emptyServices, totalSessions, operationCounter.get());
        }
    }
}