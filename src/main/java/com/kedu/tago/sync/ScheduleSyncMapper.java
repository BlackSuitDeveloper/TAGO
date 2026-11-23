package com.kedu.tago.sync;

import com.kedu.tago.sync.dto.ScheduleInsertDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 운행 일정(schedule) 동기화 매퍼 (SQL: mapper/schedule_sync_mapper.xml) */
@Mapper
public interface ScheduleSyncMapper {

    /** 일정 일괄 저장. 같은 열차·출발역·도착역·출발시각의 일정이 이미 있으면 건너뛰고, 실제로 저장된 건수를 반환 */
    int insertSchedulesIfAbsent(@Param("list") List<ScheduleInsertDTO> schedules);
}
