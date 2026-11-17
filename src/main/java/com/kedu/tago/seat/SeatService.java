package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.dto.SeatDTO;
import com.kedu.tago.seat.dto.SeatResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatMapper seatMapper;

    /**
     * 특정 일정의 좌석 목록 및 호차 요약 조회
     *
     * @param scheduleSeq 운행 일정 시퀀스 (필수)
     * @param carNo       호차 번호 (선택, 미입력 시 1호차 기본 선택)
     * @return 통합 좌석 응답 DTO
     */
    public SeatResponseDTO getSeats(Long scheduleSeq, Integer carNo) {
        // 1. 전체 호차별 잔여석 요약 정보 조회 (1~18호차 탭 렌더링용)
        List<CarSummaryDTO> carSummaryList = seatMapper.selectCarSummaries(scheduleSeq);

        // 일정이 없거나 좌석 데이터가 초기화되지 않은 경우 빈 데이터 반환
        if (carSummaryList == null || carSummaryList.isEmpty()) {
            return SeatResponseDTO.builder()
                    .scheduleSeq(scheduleSeq)
                    .carNo(carNo != null ? carNo : 1)
                    .totalSeats(0)
                    .availableSeats(0)
                    .carSummaryList(Collections.emptyList())
                    .seatList(Collections.emptyList())
                    .build();
        }

        // 2. 조회 대상 호차 결정 (미지정 시 기본 1호차)
        int targetCarNo = (carNo != null) ? carNo : 1;

        // 3. 해당 호차의 상세 좌석 목록 조회
        List<SeatDTO> seatList = seatMapper.selectSeatList(scheduleSeq, targetCarNo);

        // 4. 현재 호차의 총 좌석 수 및 잔여석 수 계산
        CarSummaryDTO currentSummary = carSummaryList.stream()
                .filter(c -> c.getCarNo().equals(targetCarNo))
                .findFirst()
                .orElse(null);

        int totalSeats = (currentSummary != null) ? currentSummary.getTotalSeats() : seatList.size();
        int availableSeats = (currentSummary != null) ? currentSummary.getAvailableSeats()
                : (int) seatList.stream().filter(s -> "AVAILABLE".equals(s.getStatus())).count();

        return SeatResponseDTO.builder()
                .scheduleSeq(scheduleSeq)
                .carNo(targetCarNo)
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .carSummaryList(carSummaryList)
                .seatList(seatList)
                .build();
    }
}
