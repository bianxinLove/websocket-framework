package com.framework.websocket.admin;

import com.framework.websocket.config.WebSocketFrameworkProperties;
import com.framework.websocket.metrics.WebSocketMetricsCollector;
import com.framework.websocket.processor.WebSocketServiceProcessor;
import com.framework.websocket.session.WebSocketSessionManager;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket管理API控制器
 * 提供会话管理、指标查询等管理功能
 * 
 * @author bianxin
 * @version 1.0.0
 */
@RestController
@RequestMapping("/websocket/admin")
@ConditionalOnProperty(name = "websocket.framework.features.admin-api", havingValue = "true", matchIfMissing = true)
public class WebSocketAdminController {

    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private WebSocketMetricsCollector metricsCollector;
    
    @Autowired
    private WebSocketFrameworkProperties properties;
    
    @Autowired
    private WebSocketServiceProcessor serviceProcessor;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<HealthStatus> health() {
        HealthStatus status = new HealthStatus();
        status.setStatus("UP");
        status.setTotalConnections(sessionManager.getTotalOnlineCount());
        status.setFrameworkVersion("1.0.0");
        
        Map<String, Object> details = new HashMap<>();
        details.put("redis", "UP"); // 可以扩展Redis连接检查
        details.put("eventBus", "UP");
        status.setDetails(details);
        
        return ResponseEntity.ok(status);
    }

    /**
     * 获取指标信息
     */
    @GetMapping("/metrics")
    public ResponseEntity<WebSocketMetricsCollector.MetricsSnapshot> metrics() {
        return ResponseEntity.ok(metricsCollector.getMetricsSnapshot());
    }

    /**
     * 获取在线用户列表
     */
    @GetMapping("/sessions/{service}/users")
    public ResponseEntity<Set<String>> getOnlineUsers(@PathVariable String service) {
        Set<String> onlineUsers = sessionManager.getOnlineUsers(service);
        return ResponseEntity.ok(onlineUsers);
    }

    /**
     * 获取服务连接数
     */
    @GetMapping("/sessions/{service}/count")
    public ResponseEntity<Integer> getServiceConnectionCount(@PathVariable String service) {
        int count = sessionManager.getOnlineCount(service);
        return ResponseEntity.ok(count);
    }

    /**
     * 发送消息给指定用户
     */
    @PostMapping("/sessions/{service}/{userId}/send")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable String service, 
            @PathVariable String userId, 
            @RequestBody SendMessageRequest request) {
        
        boolean success = sessionManager.sendMessage(service, userId, request.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "消息发送成功" : "用户不在线或发送失败");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 广播消息给服务的所有用户
     */
    @PostMapping("/sessions/{service}/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(
            @PathVariable String service, 
            @RequestBody SendMessageRequest request) {
        
        int onlineCount = sessionManager.getOnlineCount(service);
        sessionManager.broadcast(service, request.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "广播消息发送成功");
        response.put("receiverCount", onlineCount);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<WebSocketFrameworkProperties> getConfig() {
        return ResponseEntity.ok(properties);
    }

    /**
     * 获取注册的WebSocket服务列表
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, WebSocketServiceProcessor.WebSocketServiceInfo>> getServices() {
        Map<String, WebSocketServiceProcessor.WebSocketServiceInfo> services = serviceProcessor.getAllServices();
        return ResponseEntity.ok(services);
    }

    /**
     * 获取指定服务的详细信息
     */
    @GetMapping("/services/{serviceId}")
    public ResponseEntity<Map<String, Object>> getServiceDetail(@PathVariable String serviceId) {
        WebSocketServiceProcessor.WebSocketServiceInfo serviceInfo = serviceProcessor.getServiceInfo(serviceId);
        
        if (serviceInfo == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "服务不存在");
            error.put("serviceId", serviceId);
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("serviceInfo", serviceInfo);
        result.put("onlineCount", sessionManager.getOnlineCount(serviceId));
        result.put("onlineUsers", sessionManager.getOnlineUsers(serviceId));
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取服务统计概览
     */
    @GetMapping("/services/overview")
    public ResponseEntity<ServiceOverview> getServicesOverview() {
        Map<String, WebSocketServiceProcessor.WebSocketServiceInfo> allServices = serviceProcessor.getAllServices();
        
        ServiceOverview overview = new ServiceOverview();
        overview.setTotalServices(allServices.size());
        overview.setEnabledServices((int) serviceProcessor.getEnabledServiceCount());
        overview.setDisabledServices(allServices.size() - (int) serviceProcessor.getEnabledServiceCount());
        
        Map<String, Integer> connectionsByService = new HashMap<>();
        for (String serviceId : allServices.keySet()) {
            connectionsByService.put(serviceId, sessionManager.getOnlineCount(serviceId));
        }
        overview.setConnectionsByService(connectionsByService);
        
        return ResponseEntity.ok(overview);
    }

    /**
     * 健康状态数据类
     */
    @Data
    public static class HealthStatus {
        private String status;
        private int totalConnections;
        private String frameworkVersion;
        private Map<String, Object> details;
    }

    /**
     * 发送消息请求数据类
     */
    @Data
    public static class SendMessageRequest {
        private String message;
    }

    /**
     * 服务概览数据类
     */
    @Data
    public static class ServiceOverview {
        private int totalServices;
        private int enabledServices;
        private int disabledServices;
        private Map<String, Integer> connectionsByService;
    }
}