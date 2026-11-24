package com.kedu.tago.schedule;

import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 운행 시간표 목록 (page, size 로 페이징). data = 현재 페이지 목록, totalCount = 조건에 맞는 전체 건수 */
    @GetMapping
    public ResWrapper<List<ScheduleResponseDTO>> getSchedules(@ModelAttribute ScheduleSearchRequestDTO searchDTO) {
        return ResWrapper.list(scheduleService.getScheduleList(searchDTO));
    }

    @GetMapping("/{scheduleSeq}")
    public ResWrapper<ScheduleResponseDTO> getScheduleDetail(@PathVariable Long scheduleSeq) {
        ScheduleResponseDTO detail = scheduleService.getScheduleDetail(scheduleSeq);
        if (detail == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return ResWrapper.ok(detail);
    }
}
