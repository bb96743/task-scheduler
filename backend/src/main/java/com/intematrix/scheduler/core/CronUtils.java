package com.intematrix.scheduler.core;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * cron 工具。统一按秒精度计算下次触发时间。
 */
public final class CronUtils {

    private CronUtils() {
    }

    public static boolean isValid(String cron) {
        return cron != null && CronExpression.isValidExpression(cron);
    }

    /**
     * 计算 after 之后的下一个触发时间（秒精度）。
     */
    public static LocalDateTime next(String cron, LocalDateTime after) {
        CronExpression expression = CronExpression.parse(cron);
        LocalDateTime base = after.truncatedTo(ChronoUnit.SECONDS);
        return expression.next(base);
    }
}
