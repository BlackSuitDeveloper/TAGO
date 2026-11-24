package com.kedu.tago.schedule;

import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 운행 시간표 Mapper */
@Mapper
public interface ScheduleMapper {

    /** 시간표 목록 조회 */
    List<ScheduleResponseDTO> selectScheduleList(ScheduleSearchRequestDTO searchDTO);

    /** 시간표 전체 건수 */
    long countScheduleList(ScheduleSearchRequestDTO searchDTO);

    /** 시간표 상세 조회 */
    ScheduleResponseDTO selectScheduleDetail(@Param("scheduleSeq") Long scheduleSeq);
}
