package com.framework.websocket.session;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket会话统计信息
 * 
 * @author bianxin
 * @version 1.0.0
 */
@Data
@Builder
public class SessionStatistics {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 服务标识
     */
    private String service;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 连接时间
     */
    private LocalDateTime connectTime;

    /**
     * 连接持续时间（毫秒）
     */
    private long connectionDuration;

    /**
     * 发送消息数量
     */
    private long sendMessageCount;

    /**
     * 接收消息数量
     */
    private long receiveMessageCount;

    /**
     * 最后心跳时间（时间戳）
     */
    private long lastHeartbeatTime;

    /**
     * 会话是否打开
     */
    private boolean isOpen;

    /**
     * 格式化连接持续时间
     */
    public String getFormattedConnectionDuration() {
        long seconds = connectionDuration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d小时%d分钟%d秒", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 获取消息收发比率
     */
    public double getMessageRatio() {
        if (receiveMessageCount == 0) {
            return sendMessageCount > 0 ? Double.POSITIVE_INFINITY : 0.0;
        }
        return (double) sendMessageCount / receiveMessageCount;
    }
}