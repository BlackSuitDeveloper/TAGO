package com.kedu.tago.queue;

import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.queue.dto.QueueRequestDTO;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * 대기열 진입 POST /api/queue
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResWrapper<QueueResponseDTO> enterQueue(@RequestBody QueueRequestDTO requestDTO, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return ResWrapper.ok(queueService.enterQueue(requestDTO.getScheduleSeq(), memberId));
    }

    /**
     * 대기열 순번 확인 GET /api/queue/{queueToken}
     */
    @GetMapping("/{queueToken}")
    public ResWrapper<QueueResponseDTO> getQueueRank(@PathVariable String queueToken) {
        return ResWrapper.ok(queueService.checkQueueRank(queueToken));
    }
}
