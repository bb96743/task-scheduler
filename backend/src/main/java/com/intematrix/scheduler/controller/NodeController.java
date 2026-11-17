package com.intematrix.scheduler.controller;

import com.intematrix.scheduler.common.Result;
import com.intematrix.scheduler.entity.SchedAlertRecord;
import com.intematrix.scheduler.entity.SchedNode;
import com.intematrix.scheduler.service.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 节点与告警查询接口。
 */
@Tag(name = "节点与告警", description = "节点心跳列表、告警记录")
@RestController
@RequestMapping("/api")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Operation(summary = "节点列表（含心跳与在线状态）")
    @GetMapping("/nodes")
    public Result<List<SchedNode>> nodes() {
        return Result.ok(nodeService.listNodes());
    }

    @Operation(summary = "告警记录")
    @GetMapping("/alerts")
    public Result<List<SchedAlertRecord>> alerts(@RequestParam(required = false) Long taskId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(nodeService.listAlerts(taskId, limit));
    }
}
