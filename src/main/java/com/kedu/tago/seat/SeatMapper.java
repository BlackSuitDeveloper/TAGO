package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.dto.SeatDTO;
import com.kedu.tago.seat.dto.SeatStatusUpdateDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper {

    List<SeatDTO> selectSeatList(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo);

    List<CarSummaryDTO> selectCarSummaries(@Param("scheduleSeq") Long scheduleSeq);

    SeatDTO selectSeat(@Param("scheduleSeq") Long scheduleSeq, @Param("carNo") Integer carNo, @Param("seatNo") String seatNo);

    int updateSeatStatusConditional(SeatStatusUpdateDTO dto);

    int updateSeatStatusBySeq(@Param("seatSeq") Long seatSeq, @Param("newStatus") String newStatus);
}
