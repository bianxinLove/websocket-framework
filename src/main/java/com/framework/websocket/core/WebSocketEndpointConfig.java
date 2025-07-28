package com.framework.websocket.core;

import lombok.extern.slf4j.Slf4j;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.util.List;

/**
 * WebSocket端点配置类
 * 处理WebSocket握手过程中的自定义逻辑
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
public class WebSocketEndpointConfig extends ServerEndpointConfig.Configurator {

    /**
     * 修改握手过程
     * 提取客户端信息并存储到会话属性中
     */
    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        try {
            // 提取客户端IP
            String clientIp = extractClientIp(request);
            if (clientIp != null) {
                config.getUserProperties().put(WebSocketConstants.CLIENT_IP_KEY, clientIp);
            }

            // 提取User-Agent
            String userAgent = extractUserAgent(request);
            if (userAgent != null) {
                config.getUserProperties().put(WebSocketConstants.USER_AGENT_KEY, userAgent);
            }

            // 记录连接时间
            config.getUserProperties().put(WebSocketConstants.CONNECT_TIME_KEY, System.currentTimeMillis());

            log.debug("WebSocket握手信息提取完成: clientIp={}, userAgent={}", clientIp, userAgent);

        } catch (Exception e) {
            log.error("WebSocket握手信息提取失败", e);
        }

        super.modifyHandshake(config, request, response);
    }

    /**
     * 检查连接来源
     * 可以在这里实现IP白名单、黑名单等安全策略
     */
    @Override
    public boolean checkOrigin(String originHeaderValue) {
        // 默认允许所有来源，实际使用时应该根据需求进行限制
        log.debug("WebSocket连接来源检查: origin={}", originHeaderValue);
        return super.checkOrigin(originHeaderValue);
    }

    /**
     * 选择子协议
     * 如果客户端请求了特定的子协议，可以在这里进行选择
     */
    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        log.debug("WebSocket子协议协商: supported={}, requested={}", supported, requested);
        return super.getNegotiatedSubprotocol(supported, requested);
    }

    /**
     * 选择扩展
     */
    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
        log.debug("WebSocket扩展协商: installed={}, requested={}", installed, requested);
        return super.getNegotiatedExtensions(installed, requested);
    }

    /**
     * 提取客户端真实IP地址
     * 支持代理服务器的X-Forwarded-For头
     */
    private String extractClientIp(HandshakeRequest request) {
        // 尝试从X-Forwarded-For头获取
        List<String> xffHeaders = request.getHeaders().get("X-Forwarded-For");
        if (xffHeaders != null && !xffHeaders.isEmpty()) {
            String xff = xffHeaders.get(0);
            // X-Forwarded-For可能包含多个IP，取第一个
            String[] ips = xff.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }

        // 尝试从X-Real-IP头获取
        List<String> realIpHeaders = request.getHeaders().get("X-Real-IP");
        if (realIpHeaders != null && !realIpHeaders.isEmpty()) {
            return realIpHeaders.get(0).trim();
        }

        // 尝试从Proxy-Client-IP头获取
        List<String> proxyClientIpHeaders = request.getHeaders().get("Proxy-Client-IP");
        if (proxyClientIpHeaders != null && !proxyClientIpHeaders.isEmpty()) {
            return proxyClientIpHeaders.get(0).trim();
        }

        // 尝试从WL-Proxy-Client-IP头获取
        List<String> wlProxyClientIpHeaders = request.getHeaders().get("WL-Proxy-Client-IP");
        if (wlProxyClientIpHeaders != null && !wlProxyClientIpHeaders.isEmpty()) {
            return wlProxyClientIpHeaders.get(0).trim();
        }

        // 如果都没有，返回null，让WebSocket容器使用默认的远程地址
        return null;
    }

    /**
     * 提取客户端User-Agent
     */
    private String extractUserAgent(HandshakeRequest request) {
        List<String> userAgentHeaders = request.getHeaders().get("User-Agent");
        if (userAgentHeaders != null && !userAgentHeaders.isEmpty()) {
            return userAgentHeaders.get(0);
        }
        return null;
    }
}