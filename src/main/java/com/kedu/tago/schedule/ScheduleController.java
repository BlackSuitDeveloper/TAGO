package com.kedu.tago.schedule;

import com.kedu.tago.schedule.dto.SchedulePageResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<SchedulePageResponseDTO> getSchedules(@ModelAttribute ScheduleSearchRequestDTO searchDTO) {
        return ResponseEntity.ok(scheduleService.getScheduleList(searchDTO));
    }

    @GetMapping("/{scheduleSeq}")
    public ResponseEntity<ScheduleResponseDTO> getScheduleDetail(@PathVariable Long scheduleSeq) {
        ScheduleResponseDTO detail = scheduleService.getScheduleDetail(scheduleSeq);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }
}
