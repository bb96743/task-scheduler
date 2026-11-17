package com.intematrix.scheduler.core;

import com.intematrix.scheduler.config.SchedulerProperties;
import com.intematrix.scheduler.entity.SchedNode;
import com.intematrix.scheduler.mapper.SchedNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeHeartbeatServiceTest {

    @Mock
    private SchedNodeMapper nodeMapper;

    private SchedulerProperties properties;
    private NodeHeartbeatService service;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
        properties.setNodeId("node-x");
        properties.setNodeTimeoutMs(30000);
        service = new NodeHeartbeatService(nodeMapper, properties);
    }

    @Test
    void shouldRegisterNodeWhenAbsent() {
        when(nodeMapper.selectOne(any())).thenReturn(null);
        when(nodeMapper.insert(any(SchedNode.class))).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(List.of());

        service.heartbeat();

        ArgumentCaptor<SchedNode> captor = ArgumentCaptor.forClass(SchedNode.class);
        verify(nodeMapper).insert(captor.capture());
        assertThat(captor.getValue().getNodeId()).isEqualTo("node-x");
        assertThat(captor.getValue().getStatus()).isEqualTo("ONLINE");
    }

    @Test
    void shouldRenewExistingNode() {
        SchedNode node = new SchedNode();
        node.setId(1L);
        node.setNodeId("node-x");
        node.setStatus("ONLINE");
        when(nodeMapper.selectOne(any())).thenReturn(node);
        when(nodeMapper.updateById(any(SchedNode.class))).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(List.of());

        service.heartbeat();

        verify(nodeMapper, org.mockito.Mockito.atLeastOnce()).updateById(any(SchedNode.class));
    }

    @Test
    void shouldMarkStaleNodeOffline() {
        SchedNode node = new SchedNode();
        node.setId(1L);
        node.setNodeId("node-x");
        node.setStatus("ONLINE");
        when(nodeMapper.selectOne(any())).thenReturn(node);

        SchedNode stale = new SchedNode();
        stale.setId(2L);
        stale.setNodeId("node-old");
        stale.setStatus("ONLINE");
        stale.setLastHeartbeat(LocalDateTime.now().minusMinutes(10));
        when(nodeMapper.selectList(any())).thenReturn(List.of(stale));
        when(nodeMapper.updateById(any(SchedNode.class))).thenReturn(1);

        service.heartbeat();

        ArgumentCaptor<SchedNode> captor = ArgumentCaptor.forClass(SchedNode.class);
        verify(nodeMapper, org.mockito.Mockito.atLeast(2)).updateById(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(n -> "OFFLINE".equals(n.getStatus()));
    }
}
