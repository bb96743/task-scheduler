package com.intematrix.scheduler.controller;

import com.intematrix.scheduler.common.Result;
import com.intematrix.scheduler.entity.SchedTaskExecLog;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.entity.SchedTaskShard;
import com.intematrix.scheduler.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行记录与操作接口。
 */
@Tag(name = "执行管理", description = "执行详情、日志查询、手动重试、手工补偿")
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @Operation(summary = "执行详情")
    @GetMapping("/{id}")
    public Result<SchedTaskExecution> detail(@PathVariable Long id) {
        return Result.ok(executionService.getById(id));
    }

    @Operation(summary = "执行日志查询（按级别与时间）")
    @GetMapping("/{id}/logs")
    public Result<List<SchedTaskExecLog>> logs(
            @PathVariable Long id,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.ok(executionService.logs(id, level, startTime, endTime));
    }

    @Operation(summary = "分片明细")
    @GetMapping("/{id}/shards")
    public Result<List<SchedTaskShard>> shards(@PathVariable Long id) {
        return Result.ok(executionService.shards(id));
    }

    @Operation(summary = "手动重试")
    @PostMapping("/{id}/retry")
    public Result<SchedTaskExecution> retry(@PathVariable Long id) {
        return Result.ok(executionService.retry(id));
    }

    @Operation(summary = "手工补偿（仅失败终态、7 天内、幂等）")
    @PostMapping("/{id}/compensate")
    public Result<SchedTaskExecution> compensate(@PathVariable Long id) {
        return Result.ok(executionService.compensate(id));
    }
}
