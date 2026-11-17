package com.kedu.tago.schedule;

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
    public ResponseEntity<SchedulePageResDTO> getSchedules(@ModelAttribute ScheduleSearchDTO searchDTO) {
        return ResponseEntity.ok(scheduleService.getScheduleList(searchDTO));
    }

    @GetMapping("/{scheduleSeq}")
    public ResponseEntity<ScheduleDTO> getScheduleDetail(@PathVariable Long scheduleSeq) {
        ScheduleDTO detail = scheduleService.getScheduleDetail(scheduleSeq);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }
}
