package com.intematrix.scheduler.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intematrix.scheduler.config.SchedulerProperties;
import com.intematrix.scheduler.entity.SchedNode;
import com.intematrix.scheduler.mapper.SchedNodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 节点心跳：注册本节点、续约，并识别超时节点为离线。
 */
@Slf4j
@Component
public class NodeHeartbeatService {

    private final SchedNodeMapper nodeMapper;
    private final SchedulerProperties properties;

    public NodeHeartbeatService(SchedNodeMapper nodeMapper, SchedulerProperties properties) {
        this.nodeMapper = nodeMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${scheduler.heartbeat-interval-ms:5000}")
    public void heartbeat() {
        String nodeId = properties.getNodeId();
        SchedNode node = nodeMapper.selectOne(new LambdaQueryWrapper<SchedNode>()
                .eq(SchedNode::getNodeId, nodeId)
                .last("LIMIT 1"));
        if (node == null) {
            node = new SchedNode();
            node.setNodeId(nodeId);
            node.setHost(resolveHost());
            node.setLastHeartbeat(LocalDateTime.now());
            node.setStatus("ONLINE");
            node.setCreateTime(LocalDateTime.now());
            nodeMapper.insert(node);
        } else {
            node.setLastHeartbeat(LocalDateTime.now());
            node.setStatus("ONLINE");
            nodeMapper.updateById(node);
        }

        markStaleNodesOffline();
    }

    private void markStaleNodesOffline() {
        LocalDateTime deadline = LocalDateTime.now().minusNanos(properties.getNodeTimeoutMs() * 1_000_000);
        List<SchedNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<SchedNode>()
                .eq(SchedNode::getStatus, "ONLINE")
                .lt(SchedNode::getLastHeartbeat, deadline));
        for (SchedNode stale : nodes) {
            stale.setStatus("OFFLINE");
            nodeMapper.updateById(stale);
            log.warn("节点 {} 心跳超时，标记为 OFFLINE", stale.getNodeId());
        }
    }

    private String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
