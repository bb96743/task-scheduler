package com.intematrix.scheduler.core;

import com.intematrix.scheduler.common.BizException;
import com.intematrix.scheduler.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理器注册表。按 handler 名解析 Spring Bean。
 */
@Slf4j
@Component
public class HandlerRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, TaskHandler> cache = new ConcurrentHashMap<>();

    public HandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public TaskHandler resolve(String handlerName) {
        TaskHandler handler = cache.computeIfAbsent(handlerName, name -> {
            if (!applicationContext.containsBean(name)) {
                throw new BizException(ErrorCode.HANDLER_NOT_FOUND, name);
            }
            Object bean = applicationContext.getBean(name);
            if (!(bean instanceof TaskHandler taskHandler)) {
                throw new BizException(ErrorCode.HANDLER_NOT_FOUND, name + " 不是 TaskHandler 实现");
            }
            return taskHandler;
        });
        return handler;
    }

    public boolean exists(String handlerName) {
        if (!applicationContext.containsBean(handlerName)) {
            return false;
        }
        return applicationContext.getBean(handlerName) instanceof TaskHandler;
    }
}
