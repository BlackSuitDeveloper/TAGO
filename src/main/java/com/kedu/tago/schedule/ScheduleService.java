package com.kedu.tago.schedule;

import com.kedu.tago.common.paging.PageResult;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;

    public PageResult<ScheduleResponseDTO> getScheduleList(ScheduleSearchRequestDTO searchDTO) {
        List<ScheduleResponseDTO> schedules = scheduleMapper.selectScheduleList(searchDTO);
        long totalCount = scheduleMapper.countScheduleList(searchDTO);
        return PageResult.of(schedules, totalCount, searchDTO);
    }

    public ScheduleResponseDTO getScheduleDetail(Long scheduleSeq) {
        return scheduleMapper.selectScheduleDetail(scheduleSeq);
    }
}
