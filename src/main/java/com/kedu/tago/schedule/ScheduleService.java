package com.kedu.tago.schedule;

import com.kedu.tago.common.paging.PageResult;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 운행 시간표 조회 서비스 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;

    /** 시간표 목록 페이징 조회 */
    public PageResult<ScheduleResponseDTO> getScheduleList(ScheduleSearchRequestDTO searchDTO) {
        List<ScheduleResponseDTO> schedules = scheduleMapper.selectScheduleList(searchDTO);
        long totalCount = scheduleMapper.countScheduleList(searchDTO);
        return PageResult.of(schedules, totalCount, searchDTO);
    }

    /** 시간표 상세 조회 */
    public ScheduleResponseDTO getScheduleDetail(Long scheduleSeq) {
        return scheduleMapper.selectScheduleDetail(scheduleSeq);
    }
}
