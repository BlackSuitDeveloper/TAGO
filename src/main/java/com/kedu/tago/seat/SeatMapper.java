package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.dto.SeatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper {

    List<SeatDTO> selectSeatList(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo);

    List<CarSummaryDTO> selectCarSummaries(@Param("scheduleSeq") Long scheduleSeq);

    SeatDTO selectSeat(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo, @Param("seatNo") String seatNo);

    int insertOccupiedSeat(SeatDTO seat);

    int deleteSeatBySeq(@Param("seatSeq") Long seatSeq);
}
