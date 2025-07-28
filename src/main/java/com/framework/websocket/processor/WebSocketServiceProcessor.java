package com.framework.websocket.processor;

import com.framework.websocket.annotation.WebSocketService;
import com.framework.websocket.handler.WebSocketMessageHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebSocket服务注解处理器
 * 自动扫描和注册带有@WebSocketService注解的服务
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Slf4j
@Component
public class WebSocketServiceProcessor implements BeanPostProcessor {

    /**
     * 注册的WebSocket服务信息
     */
    private final Map<String, WebSocketServiceInfo> registeredServices = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        
        // 检查是否实现了WebSocketMessageHandler接口
        if (WebSocketMessageHandler.class.isAssignableFrom(beanClass)) {
            // 查找@WebSocketService注解
            WebSocketService annotation = AnnotationUtils.findAnnotation(beanClass, WebSocketService.class);
            
            if (annotation != null) {
                registerWebSocketService(bean, annotation, beanName);
            }
        }
        
        return bean;
    }

    /**
     * 注册WebSocket服务
     */
    private void registerWebSocketService(Object bean, WebSocketService annotation, String beanName) {
        if (!annotation.enabled()) {
            log.info("WebSocket服务已禁用，跳过注册: service={}, bean={}", annotation.value(), beanName);
            return;
        }

        WebSocketMessageHandler<?> handler = (WebSocketMessageHandler<?>) bean;
        
        // 创建服务信息
        WebSocketServiceInfo serviceInfo = new WebSocketServiceInfo();
        serviceInfo.setServiceId(annotation.value());
        serviceInfo.setServiceName(annotation.name().isEmpty() ? annotation.value() : annotation.name());
        serviceInfo.setDescription(annotation.description());
        serviceInfo.setEnabled(annotation.enabled());
        serviceInfo.setBeanName(beanName);
        serviceInfo.setBeanClass(bean.getClass());
        serviceInfo.setHandler(handler);
        
        // 获取支持的服务列表（如果实现了getSupportedServices方法）
        try {
            String[] supportedServices = handler.getSupportedServices();
            if (supportedServices != null && supportedServices.length > 0) {
                serviceInfo.setSupportedServices(new HashSet<>(Arrays.asList(supportedServices)));
                
                // 验证注解value是否在支持的服务列表中
                if (!new HashSet<>(Arrays.asList(supportedServices)).contains(annotation.value())) {
                    log.warn("注解@WebSocketService的value({})不在getSupportedServices返回的列表中: {}", 
                        annotation.value(), supportedServices);
                }
            }
        } catch (Exception e) {
            log.warn("获取服务支持列表失败: service={}, bean={}", annotation.value(), beanName, e);
        }
        
        // 注册服务
        registeredServices.put(annotation.value(), serviceInfo);
        
        log.info("WebSocket服务注册成功: serviceId={}, serviceName={}, beanName={}, class={}", 
            annotation.value(), serviceInfo.getServiceName(), beanName, bean.getClass().getSimpleName());
    }

    /**
     * 获取已注册的服务信息
     */
    public WebSocketServiceInfo getServiceInfo(String serviceId) {
        return registeredServices.get(serviceId);
    }

    /**
     * 获取所有已注册的服务
     */
    public Map<String, WebSocketServiceInfo> getAllServices() {
        return new ConcurrentHashMap<>(registeredServices);
    }

    /**
     * 获取启用的服务数量
     */
    public long getEnabledServiceCount() {
        return registeredServices.values().stream()
            .filter(WebSocketServiceInfo::isEnabled)
            .count();
    }

    /**
     * 检查服务是否存在
     */
    public boolean isServiceRegistered(String serviceId) {
        return registeredServices.containsKey(serviceId);
    }

    /**
     * 检查服务是否启用
     */
    public boolean isServiceEnabled(String serviceId) {
        WebSocketServiceInfo serviceInfo = registeredServices.get(serviceId);
        return serviceInfo != null && serviceInfo.isEnabled();
    }

    /**
     * WebSocket服务信息
     */
    @Data
    public static class WebSocketServiceInfo {
        private String serviceId;
        private String serviceName;
        private String description;
        private boolean enabled;
        private String beanName;
        private Class<?> beanClass;
        private WebSocketMessageHandler<?> handler;
        private Set<String> supportedServices;
    }
}