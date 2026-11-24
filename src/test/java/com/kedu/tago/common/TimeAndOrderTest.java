package com.kedu.tago.common;

import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.schedule.ScheduleController;
import com.kedu.tago.schedule.ScheduleService;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static com.kedu.tago.common.ApiPaths.V1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시각은 시간대 없이 한국 시각 그대로, 역 목록은 DB 콜레이션과 무관하게 가나다순 */
class TimeAndOrderTest {

    private static final LocalDateTime DEP = LocalDateTime.of(2026, 11, 25, 5, 13, 0);

    private MockMvc scheduleMvc(ScheduleResponseDTO dto) {
        ScheduleService service = mock(ScheduleService.class);
        when(service.getScheduleDetail(1L)).thenReturn(dto);
        return MockMvcBuilders.standaloneSetup(new ScheduleController(service)).build();
    }

    @Test
    @DisplayName("일정 시각은 Z 나 시간대 표시 없이 저장된 한국 시각 그대로 나간다")
    void scheduleTimesHaveNoZone() throws Exception {
        ScheduleResponseDTO dto = ScheduleResponseDTO.builder()
                .scheduleSeq(1L).depPlandTime(DEP).arrPlandTime(DEP.plusHours(2)).bookingOpenAt(DEP.minusMonths(1)).build();

        scheduleMvc(dto).perform(get(V1 + "/schedule/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.depPlandTime").value("2026-11-25T05:13:00"))
                .andExpect(jsonPath("$.data.arrPlandTime").value("2026-11-25T07:13:00"))
                .andExpect(jsonPath("$.data.bookingOpenAt").value("2026-10-25T05:13:00"));
    }

    @Test
    @DisplayName("서버 JVM 시간대가 UTC 여도 일정 시각이 변하지 않는다")
    void scheduleTimesIgnoreServerTimeZone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            ScheduleResponseDTO dto = ScheduleResponseDTO.builder().scheduleSeq(1L).depPlandTime(DEP).build();
            scheduleMvc(dto).perform(get(V1 + "/schedule/1"))
                    .andExpect(jsonPath("$.data.depPlandTime").value("2026-11-25T05:13:00"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("시각 필드는 시간대를 품는 타입(Timestamp 등)이 아니라 LocalDateTime 이다")
    void timeFieldsAreLocalDateTime() throws Exception {
        for (String name : new String[]{"depPlandTime", "arrPlandTime", "bookingOpenAt"}) {
            assertEquals(LocalDateTime.class, field(ScheduleResponseDTO.class, name).getType(), name);
        }
        assertEquals(LocalDateTime.class, field(MemberProfileResponseDTO.class, "createdAt").getType());
    }

    @Test
    @DisplayName("역 목록 SQL 은 정렬을 콜레이션에 맡기지 않고 COLLATE \"C\" 로 고정한다")
    void stationOrderIsCollationIndependent() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/station_mapper.xml"));
        assertTrue(xml.contains("ORDER BY st.nodename COLLATE \"C\""), "역 목록 정렬에 COLLATE \"C\" 가 필요하다");
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }
}
