package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.dto.SeatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 좌석 Mapper */
@Mapper
public interface SeatMapper {

    /** 호차별 점유 좌석 목록 조회 */
    List<SeatDTO> selectSeatList(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo);

    /** 호차별 점유 좌석 수 조회 */
    List<CarSummaryDTO> selectCarSummaries(@Param("scheduleSeq") Long scheduleSeq);

    /** 좌석 단건 조회 */
    SeatDTO selectSeat(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo, @Param("seatNo") String seatNo);

    /** 점유 좌석 등록 */
    int insertOccupiedSeat(SeatDTO seat);

    /** 좌석 삭제 (점유 해제) */
    int deleteSeatBySeq(@Param("seatSeq") Long seatSeq);
}
