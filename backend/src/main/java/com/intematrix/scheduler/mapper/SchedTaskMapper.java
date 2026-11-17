package com.intematrix.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intematrix.scheduler.entity.SchedTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SchedTaskMapper extends BaseMapper<SchedTask> {
}
