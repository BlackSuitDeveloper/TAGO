package com.kedu.tago.reservation;

import com.kedu.tago.reservation.dto.MyTicketResponseDTO;
import com.kedu.tago.reservation.dto.ReservationDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReservationMapper {

    int insertReservation(ReservationDTO reservation);

    ReservationDTO selectReservationById(@Param("reservationSeq") Long reservationSeq);

    int updateReservationStatus(
            @Param("reservationSeq") Long reservationSeq,
            @Param("status") String status,
            @Param("cancelReason") String cancelReason
    );

    int confirmReservation(@Param("reservationSeq") Long reservationSeq);

    List<ReservationDTO> selectExpiredHeldReservations();

    List<MyTicketResponseDTO> selectMyReservations(@Param("memberId") String memberId);
}
