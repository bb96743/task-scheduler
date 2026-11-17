package com.intematrix.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intematrix.scheduler.entity.SchedAlertRecord;
import com.intematrix.scheduler.entity.SchedNode;
import com.intematrix.scheduler.mapper.SchedAlertRecordMapper;
import com.intematrix.scheduler.mapper.SchedNodeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 节点与告警查询服务。
 */
@Service
public class NodeService {

    private final SchedNodeMapper nodeMapper;
    private final SchedAlertRecordMapper alertRecordMapper;

    public NodeService(SchedNodeMapper nodeMapper, SchedAlertRecordMapper alertRecordMapper) {
        this.nodeMapper = nodeMapper;
        this.alertRecordMapper = alertRecordMapper;
    }

    public List<SchedNode> listNodes() {
        return nodeMapper.selectList(new LambdaQueryWrapper<SchedNode>()
                .orderByAsc(SchedNode::getNodeId));
    }

    public List<SchedAlertRecord> listAlerts(Long taskId, int limit) {
        return alertRecordMapper.selectList(new LambdaQueryWrapper<SchedAlertRecord>()
                .eq(taskId != null, SchedAlertRecord::getTaskId, taskId)
                .orderByDesc(SchedAlertRecord::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}
