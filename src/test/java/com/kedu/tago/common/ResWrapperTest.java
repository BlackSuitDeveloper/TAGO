package com.kedu.tago.common;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.exception.GlobalExceptionHandler;
import com.kedu.tago.queue.QueueController;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import com.kedu.tago.reservation.ReservationController;
import com.kedu.tago.reservation.ReservationService;
import com.kedu.tago.schedule.ScheduleController;
import com.kedu.tago.schedule.ScheduleService;
import com.kedu.tago.station.StationController;
import com.kedu.tago.station.StationService;
import com.kedu.tago.station.dto.StationResponseDTO;
import com.kedu.tago.wishlist.WishListController;
import com.kedu.tago.wishlist.WishListService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 응답 형식(ResWrapper) 검증. DB·Redis 없이 컨트롤러와 예외 처리기만 띄운다.
 *  - 일반:  { success:true, data }            (+ message)
 *  - 목록:  { success:true, data:[], totalCount }
 *  - 에러:  { success:false, status, message }
 */
class ResWrapperTest {

    private final StationService stationService = mock(StationService.class);
    private final ScheduleService scheduleService = mock(ScheduleService.class);
    private final QueueService queueService = mock(QueueService.class);
    private final ReservationService reservationService = mock(ReservationService.class);
    private final WishListService wishListService = mock(WishListService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(
                    new StationController(stationService),
                    new ScheduleController(scheduleService),
                    new QueueController(queueService),
                    new ReservationController(reservationService),
                    new WishListController(wishListService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("목록 응답: data 는 배열, totalCount 가 있고 status 는 없다")
    void list() throws Exception {
        when(stationService.getStationList()).thenReturn(List.of(
                StationResponseDTO.builder().nodeId("NAT010000").nodeName("서울").build(),
                StationResponseDTO.builder().nodeId("NAT011668").nodeName("부산").build()));

        mvc.perform(get("/station"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].nodeName").value("서울"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    @DisplayName("일반 응답: data 만 있고 totalCount 는 없다 (201 은 @ResponseStatus 로 유지)")
    void ok() throws Exception {
        when(queueService.enterQueue(anyLong(), any())).thenReturn(
                QueueResponseDTO.builder().queueToken("tok").queueRank(1L).isAvailable(true).build());

        mvc.perform(post("/queue").contentType(MediaType.APPLICATION_JSON).content("{\"scheduleSeq\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.queueToken").value("tok"))
                .andExpect(jsonPath("$.totalCount").doesNotExist());
    }

    @Test
    @DisplayName("본문 없는 응답: message 만 있고 data 는 생략된다")
    void done() throws Exception {
        mvc.perform(post("/wishlist/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("찜 목록에 추가했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("에러: ApiException(404) -> { success:false, status:404, message }")
    void apiException() throws Exception {
        when(scheduleService.getScheduleDetail(anyLong())).thenReturn(null);

        mvc.perform(get("/schedule/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("존재하지 않는 운행 일정입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("에러: ErrorCode 의 번호·HTTP 상태가 응답에 나가고, 상황별 문구로 바꿀 수 있다")
    void errorCodeWithCustomMessage() throws Exception {
        when(reservationService.holdSeats(any(), anyString()))
                .thenThrow(new ApiException(ErrorCode.SEAT_ALREADY_TAKEN, "좌석 3A은(는) 이미 선택되었습니다."));

        mvc.perform(post("/reservation")
                        .requestAttr("id", "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleSeq\":1,\"carNo\":1,\"seatList\":[\"3A\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.message").value("좌석 3A은(는) 이미 선택되었습니다."));
    }

    @Test
    @DisplayName("에러: ErrorCode 를 거치지 않은 ResponseStatusException 도 같은 형식(HTTP 상태 유지, 대표 코드 1000)")
    void legacyResponseStatusException() throws Exception {
        when(reservationService.holdSeats(any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.GONE, "만료"));

        mvc.perform(post("/reservation")
                        .requestAttr("id", "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleSeq\":1,\"carNo\":1,\"seatList\":[\"3A\"]}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("만료"));
    }

    @Test
    @DisplayName("에러: 깨진 JSON 은 400, 내부 예외 메시지는 노출하지 않는다")
    void badRequestAndUnexpected() throws Exception {
        mvc.perform(post("/queue").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(1000));

        when(stationService.getStationList()).thenThrow(new IllegalStateException("db password=secret"));

        mvc.perform(get("/station"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value(9000))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
