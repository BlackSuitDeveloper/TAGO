package com.kedu.tago.schedule;

import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
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

/** 운행 시간표 조회 API */
@RestController
@RequestMapping(ApiPaths.V1 + "/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 운행 시간표 목록 페이징 조회 (GET /api/v1/schedule) */
    @GetMapping
    public ResWrapper<List<ScheduleResponseDTO>> getSchedules(@ModelAttribute ScheduleSearchRequestDTO searchDTO) {
        return ResWrapper.list(scheduleService.getScheduleList(searchDTO));
    }

    /** 운행 시간표 상세 조회 (GET /api/v1/schedule/{scheduleSeq}) */
    @GetMapping("/{scheduleSeq}")
    public ResWrapper<ScheduleResponseDTO> getScheduleDetail(@PathVariable Long scheduleSeq) {
        ScheduleResponseDTO detail = scheduleService.getScheduleDetail(scheduleSeq);
        if (detail == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return ResWrapper.ok(detail);
    }
}
