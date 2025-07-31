package com.framework.websocket.monitor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 监控页面访问控制器
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Controller
public class MonitorViewController {

    /**
     * 访问线程池监控页面
     */
    @GetMapping("/monitor")
    public String monitorPage() {
        return "redirect:/threadpool-monitor.html";
    }

    /**
     * 访问线程池监控页面（完整路径）
     */
    @GetMapping("/monitor/threadpool")
    public String threadPoolMonitorPage() {
        return "redirect:/threadpool-monitor.html";
    }
}