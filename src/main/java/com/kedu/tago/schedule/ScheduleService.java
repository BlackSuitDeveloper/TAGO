package com.kedu.tago.schedule;

import com.kedu.tago.schedule.dto.SchedulePageResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;

    public List<ScheduleResponseDTO> getListBySchedule() {
        return scheduleMapper.selectScheduleList(new ScheduleSearchRequestDTO());
    }

    public SchedulePageResponseDTO getScheduleList(ScheduleSearchRequestDTO searchDTO) {
        int page = searchDTO.getSafePage();
        int size = searchDTO.getSafeSize();

        List<ScheduleResponseDTO> schedules = scheduleMapper.selectScheduleList(searchDTO);
        long totalCount = scheduleMapper.countScheduleList(searchDTO);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        return SchedulePageResponseDTO.builder()
                .schedules(schedules)
                .page(page)
                .size(size)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    public ScheduleResponseDTO getScheduleDetail(Long scheduleSeq) {
        return scheduleMapper.selectScheduleDetail(scheduleSeq);
    }
}
