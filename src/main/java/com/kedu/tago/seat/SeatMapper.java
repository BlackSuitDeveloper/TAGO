package com.kedu.tago.seat;

import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.dto.SeatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 좌석 Mapper */
@Mapper
public interface SeatMapper {

    /** 호차별 확정 좌석 목록 조회 */
    List<SeatDTO> selectSeatList(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo);

    /** 호차별 확정 좌석 수 조회 */
    List<CarSummaryDTO> selectCarSummaries(@Param("scheduleSeq") Long scheduleSeq);

    /** 확정 좌석 단건 조회 */
    SeatDTO selectSeat(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo, @Param("seatNo") String seatNo);

    /** 여러 좌석 중 이미 확정된 좌석 조회 */
    List<SeatDTO> selectSeatsIn(@Param("scheduleSeq") Long scheduleSeq, @Param("seats") List<ReservationRequestDTO.SeatSelection> seats);

    /** 확정 좌석 등록 */
    int insertOccupiedSeat(SeatDTO seat);

    /** 좌석 삭제 (예매 취소) */
    int deleteSeatBySeq(@Param("seatSeq") Long seatSeq);
}
