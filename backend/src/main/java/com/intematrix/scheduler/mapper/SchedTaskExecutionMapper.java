package com.intematrix.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SchedTaskExecutionMapper extends BaseMapper<SchedTaskExecution> {

    /**
     * 原子地把 RUNNING 状态写为已开始，返回受影响行数，用于避免重复处理。
     */
    @Update("UPDATE sched_task_execution SET start_time = #{startTime} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND start_time IS NULL")
    int updateStatusToRunning(@Param("id") Long id, @Param("startTime") LocalDateTime startTime);
}
