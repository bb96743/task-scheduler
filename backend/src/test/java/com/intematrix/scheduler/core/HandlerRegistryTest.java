package com.intematrix.scheduler.core;

import com.intematrix.scheduler.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlerRegistryTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void shouldResolveRegisteredHandler() {
        TaskHandler handler = ctx -> ShardResult.ok("ok");
        when(applicationContext.containsBean("h1")).thenReturn(true);
        when(applicationContext.getBean("h1")).thenReturn(handler);

        HandlerRegistry registry = new HandlerRegistry(applicationContext);
        assertThat(registry.resolve("h1")).isSameAs(handler);
        assertThat(registry.exists("h1")).isTrue();
    }

    @Test
    void shouldThrowWhenBeanMissing() {
        when(applicationContext.containsBean("missing")).thenReturn(false);
        HandlerRegistry registry = new HandlerRegistry(applicationContext);

        assertThatThrownBy(() -> registry.resolve("missing")).isInstanceOf(BizException.class);
        assertThat(registry.exists("missing")).isFalse();
    }

    @Test
    void shouldThrowWhenBeanIsNotHandler() {
        when(applicationContext.containsBean("other")).thenReturn(true);
        when(applicationContext.getBean("other")).thenReturn(new Object());
        HandlerRegistry registry = new HandlerRegistry(applicationContext);

        assertThatThrownBy(() -> registry.resolve("other")).isInstanceOf(BizException.class);
        assertThat(registry.exists("other")).isFalse();
    }
}
