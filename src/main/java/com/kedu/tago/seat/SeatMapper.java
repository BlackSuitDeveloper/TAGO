package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.dto.SeatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper {

    List<SeatDTO> selectSeatList(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo);
    // 특정 일정 및 호차의 좌석 목록 조회
    // @param scheduleSeq 운행 일정 시퀀스 @param carNo (호차 번호 (null일 경우 전체 좌석 조회)) @return 좌석 목록

    List<CarSummaryDTO> selectCarSummaries(@Param("scheduleSeq") Long scheduleSeq);
    // 호차별 총 좌석 수 및 잔여(AVAILABLE) 좌석 수 요약 목록 조회
    // @param scheduleSeq 운행 일정 시퀀스 @return 각 호차별 요약 목록
}
