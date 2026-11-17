package com.intematrix.scheduler.service;

import com.intematrix.scheduler.entity.SchedAlertRecord;
import com.intematrix.scheduler.entity.SchedNode;
import com.intematrix.scheduler.mapper.SchedAlertRecordMapper;
import com.intematrix.scheduler.mapper.SchedNodeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock
    private SchedNodeMapper nodeMapper;
    @Mock
    private SchedAlertRecordMapper alertRecordMapper;

    @InjectMocks
    private NodeService nodeService;

    @Test
    void shouldListNodes() {
        SchedNode node = new SchedNode();
        node.setNodeId("n1");
        when(nodeMapper.selectList(any())).thenReturn(List.of(node));

        assertThat(nodeService.listNodes()).hasSize(1);
    }

    @Test
    void shouldListAlertsWithClampedLimit() {
        when(alertRecordMapper.selectList(any())).thenReturn(List.of(new SchedAlertRecord()));

        assertThat(nodeService.listAlerts(1L, 1000)).hasSize(1);
        assertThat(nodeService.listAlerts(null, 0)).hasSize(1);
    }
}
