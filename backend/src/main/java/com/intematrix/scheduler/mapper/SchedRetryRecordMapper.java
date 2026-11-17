package com.intematrix.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.intematrix.scheduler.entity.SchedRetryRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SchedRetryRecordMapper extends BaseMapper<SchedRetryRecord> {
}
