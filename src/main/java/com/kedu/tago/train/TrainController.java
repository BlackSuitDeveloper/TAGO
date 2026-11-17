package com.kedu.tago.train;

import com.kedu.tago.schedule.ScheduleDTO;
import com.kedu.tago.schedule.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trains")
public class TrainController {

    private final ScheduleService scheduleService;

    public TrainController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ResponseEntity<List<ScheduleDTO>> getListBySchedule() {

        return ResponseEntity.ok(scheduleService.getListBySchedule());
    }
}
