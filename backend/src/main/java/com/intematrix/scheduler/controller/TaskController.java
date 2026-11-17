package com.intematrix.scheduler.controller;

import com.intematrix.scheduler.common.Result;
import com.intematrix.scheduler.dto.AlertConfigRequest;
import com.intematrix.scheduler.dto.ShardConfigRequest;
import com.intematrix.scheduler.dto.TaskCreateRequest;
import com.intematrix.scheduler.dto.TaskStatusRequest;
import com.intematrix.scheduler.entity.SchedAlertConfig;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.service.ExecutionService;
import com.intematrix.scheduler.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务管理接口。
 */
@Tag(name = "任务管理", description = "任务注册、启停、立即执行、分片与告警配置")
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final ExecutionService executionService;

    public TaskController(TaskService taskService, ExecutionService executionService) {
        this.taskService = taskService;
        this.executionService = executionService;
    }

    @Operation(summary = "任务注册")
    @PostMapping
    public Result<SchedTask> create(@Valid @RequestBody TaskCreateRequest request) {
        return Result.ok(taskService.create(request));
    }

    @Operation(summary = "任务列表")
    @GetMapping
    public Result<List<SchedTask>> list() {
        return Result.ok(taskService.list());
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<SchedTask> detail(@PathVariable Long id) {
        return Result.ok(taskService.getById(id));
    }

    @Operation(summary = "启用/停用任务")
    @PutMapping("/{id}/status")
    public Result<SchedTask> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody TaskStatusRequest request) {
        return Result.ok(taskService.updateStatus(id, request.getStatus()));
    }

    @Operation(summary = "立即执行")
    @PostMapping("/{id}/run")
    public Result<SchedTaskExecution> runNow(@PathVariable Long id) {
        return Result.ok(taskService.runNow(id));
    }

    @Operation(summary = "执行记录查询（按任务与时间）")
    @GetMapping("/{id}/executions")
    public Result<List<SchedTaskExecution>> executions(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.ok(executionService.listByTask(id, status, startTime, endTime));
    }

    @Operation(summary = "分片配置")
    @PutMapping("/{id}/shards")
    public Result<SchedTask> updateShards(@PathVariable Long id,
                                          @Valid @RequestBody ShardConfigRequest request) {
        return Result.ok(taskService.updateShard(id, request.getShardTotal()));
    }

    @Operation(summary = "告警配置")
    @PutMapping("/{id}/alert")
    public Result<SchedAlertConfig> updateAlert(@PathVariable Long id,
                                                @Valid @RequestBody AlertConfigRequest request) {
        return Result.ok(taskService.updateAlert(id, request));
    }

    @Operation(summary = "查看告警配置")
    @GetMapping("/{id}/alert")
    public Result<SchedAlertConfig> getAlert(@PathVariable Long id) {
        return Result.ok(taskService.getAlert(id));
    }
}
