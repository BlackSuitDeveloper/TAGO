package com.kedu.tago.queue;

import com.kedu.tago.queue.dto.QueueRequestDTO;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * 대기열 진입 POST /api/queue
     */
    @PostMapping
    public ResponseEntity<QueueResponseDTO> enterQueue(@RequestBody QueueRequestDTO requestDTO, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        QueueResponseDTO response = queueService.enterQueue(requestDTO.getScheduleSeq(), memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 대기열 순번 확인 GET /api/queue/{queueToken}
     */
    @GetMapping("/{queueToken}")
    public ResponseEntity<QueueResponseDTO> getQueueRank(@PathVariable String queueToken) {
        QueueResponseDTO response = queueService.checkQueueRank(queueToken);
        return ResponseEntity.ok(response);
    }
}
