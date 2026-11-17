package com.kedu.tago.schedule;

import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScheduleMapper {

    List<ScheduleResponseDTO> selectScheduleList(ScheduleSearchRequestDTO searchDTO);

    long countScheduleList(ScheduleSearchRequestDTO searchDTO);

    ScheduleResponseDTO selectScheduleDetail(@Param("scheduleSeq") Long scheduleSeq);
}
