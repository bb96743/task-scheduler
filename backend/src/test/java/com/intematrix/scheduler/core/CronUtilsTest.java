package com.intematrix.scheduler.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CronUtilsTest {

    @Test
    void shouldRejectInvalidCron() {
        assertThat(CronUtils.isValid("not a cron")).isFalse();
        assertThat(CronUtils.isValid(null)).isFalse();
        assertThat(CronUtils.isValid("0 0 3 * * ?")).isTrue();
    }

    @Test
    void shouldComputeNextFireTime() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 17, 10, 0, 0);
        LocalDateTime next = CronUtils.next("0/30 * * * * ?", base);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 0, 30));
    }

    @Test
    void shouldSkipToNextMinuteWhenPast() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 17, 10, 0, 45);
        LocalDateTime next = CronUtils.next("0/30 * * * * ?", base);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 1, 0));
    }
}
