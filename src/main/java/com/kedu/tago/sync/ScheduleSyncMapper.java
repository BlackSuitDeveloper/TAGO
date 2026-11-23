package com.kedu.tago.sync;

import com.kedu.tago.sync.dto.ScheduleInsertDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 운행 일정(schedule) 동기화 매퍼 (SQL: mapper/schedule_sync_mapper.xml) */
@Mapper
public interface ScheduleSyncMapper {

    /** 같은 열차·출발역·도착역·출발시각의 일정이 있는지 확인 */
    boolean existsSchedule(
            @Param("trainno") int trainno,
            @Param("depNodeId") String depNodeId,
            @Param("arrNodeId") String arrNodeId,
            @Param("depTime") LocalDateTime depTime);

    /** 일정 저장 (생성된 schedule_seq 는 dto 에 채워진다) */
    int insertSchedule(ScheduleInsertDTO schedule);
}
