package com.kedu.tago.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleDAO scheduleDAO;

    public List<ScheduleDTO> getListBySchedule() {
        return scheduleDAO.selectScheduleList(new ScheduleSearchDTO());
    }

    public SchedulePageResDTO getScheduleList(ScheduleSearchDTO searchDTO) {
        int page = searchDTO.getSafePage();
        int size = searchDTO.getSafeSize();

        List<ScheduleDTO> schedules = scheduleDAO.selectScheduleList(searchDTO);
        long totalCount = scheduleDAO.countScheduleList(searchDTO);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        return SchedulePageResDTO.builder()
                .schedules(schedules)
                .page(page)
                .size(size)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    public ScheduleDTO getScheduleDetail(Long scheduleSeq) {
        return scheduleDAO.selectScheduleDetail(scheduleSeq);
    }
}
