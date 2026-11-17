package com.intematrix.scheduler.common;

import lombok.Getter;

/**
 * 错误码表（验收第 7 条要求错误码明确）。
 */
@Getter
public enum ErrorCode {

    TASK_NOT_FOUND(1001, "任务不存在"),
    TASK_NAME_DUPLICATED(1002, "任务名已存在"),
    TASK_DISABLED(1003, "任务已停用"),
    CRON_INVALID(1004, "cron 表达式不合法"),
    HANDLER_NOT_FOUND(1005, "处理器不存在"),

    EXECUTION_NOT_FOUND(2001, "执行记录不存在"),
    EXECUTION_NOT_FAILED(2002, "仅失败终态的执行可补偿"),
    EXECUTION_ALREADY_COMPENSATED(2003, "该执行已补偿，请勿重复提交"),
    EXECUTION_OUT_OF_WINDOW(2004, "超出补偿时间窗口"),
    EXECUTION_RUNNING(2005, "执行中，暂不可操作"),

    PARAM_INVALID(3001, "参数不合法"),
    INTERNAL_ERROR(5000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
