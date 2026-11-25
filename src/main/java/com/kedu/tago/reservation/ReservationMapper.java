package com.kedu.tago.reservation;

import com.kedu.tago.reservation.dto.MyTicketResponseDTO;
import com.kedu.tago.reservation.dto.ReservationDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 예매 매퍼 */
@Mapper
public interface ReservationMapper {

    /** 예매 저장 */
    int insertReservation(ReservationDTO reservation);

    /** 예매 단건 조회 */
    ReservationDTO selectReservationById(@Param("reservationSeq") Long reservationSeq);

    /** 예매 상태 및 취소 사유 변경 */
    int updateReservationStatus(
            @Param("reservationSeq") Long reservationSeq,
            @Param("status") String status,
            @Param("cancelReason") String cancelReason
    );

    /** 예매 확정 */
    int confirmReservation(@Param("reservationSeq") Long reservationSeq);

    /** TTL 만료 예약 조건부 취소 */
    int cancelExpiredReservation(@Param("reservationSeq") Long reservationSeq);

    /** 선점 시간이 만료된 HELD 예매 목록 조회 */
    List<ReservationDTO> selectExpiredHeldReservations();

    /** 회원의 승차권 목록 조회 */
    List<MyTicketResponseDTO> selectMyReservations(@Param("memberId") String memberId);

    /** 회원의 현재 유효한 HELD 선점 좌석 수 조회 */
    int countActiveHeldByMember(@Param("memberId") String memberId);
}
