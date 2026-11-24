package com.kedu.tago.common;

import com.kedu.tago.common.paging.PageParam;
import com.kedu.tago.common.paging.PageResult;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.schedule.ScheduleController;
import com.kedu.tago.schedule.ScheduleService;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.schedule.dto.ScheduleSearchRequestDTO;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공통 페이징(PageParam / PageResult) 검증 */
class PagingTest {

    @Test
    @DisplayName("기본값 page=1, size=10, offset=0")
    void defaults() {
        PageParam p = new PageParam();
        assertEquals(1, p.getSafePage());
        assertEquals(10, p.getSafeSize());
        assertEquals(0, p.getOffset());
    }

    @Test
    @DisplayName("잘못된 값은 보정한다: page<1 -> 1, size<=0 -> 기본값, size>100 -> 100")
    void clamps() {
        PageParam p = new PageParam();
        p.setPage(0);
        p.setSize(0);
        assertEquals(1, p.getSafePage());
        assertEquals(PageParam.DEFAULT_SIZE, p.getSafeSize());

        p.setPage(-5);
        p.setSize(1000);
        assertEquals(1, p.getSafePage());
        assertEquals(PageParam.MAX_SIZE, p.getSafeSize());
    }

    @Test
    @DisplayName("offset = (page-1) * size")
    void offset() {
        PageParam p = new PageParam();
        p.setPage(3);
        p.setSize(20);
        assertEquals(40, p.getOffset());
    }

    @Test
    @DisplayName("PageResult: 전체 페이지 수는 올림, 건수 0 이면 0")
    void totalPages() {
        PageParam p = new PageParam();
        p.setSize(10);
        assertEquals(3, PageResult.of(List.of(), 25, p).getTotalPages());
        assertEquals(1, PageResult.of(List.of(), 10, p).getTotalPages());
        assertEquals(0, PageResult.of(List.of(), 0, p).getTotalPages());
    }

    @Test
    @DisplayName("MyBatis 가 상속받은 getter(safeSize, offset)를 읽을 수 있다 (매퍼의 #{safeSize} #{offset})")
    void myBatisReadsInheritedProperties() {
        ScheduleSearchRequestDTO dto = new ScheduleSearchRequestDTO();
        dto.setPage(2);
        dto.setSize(5);
        var meta = SystemMetaObject.forObject(dto);
        assertEquals(5, meta.getValue("safeSize"));
        assertEquals(5, meta.getValue("offset"));
        assertEquals("ALL", meta.getValue("normalizedFilter"));
    }

    @Test
    @DisplayName("컨트롤러: page, size 쿼리가 검색 DTO 에 바인딩되고, 응답은 목록 형식(totalCount)")
    void controllerBindsPageAndReturnsListForm() throws Exception {
        ScheduleService service = mock(ScheduleService.class);
        ScheduleSearchRequestDTO[] holder = new ScheduleSearchRequestDTO[1];
        when(service.getScheduleList(any())).thenAnswer(inv -> {
            ScheduleSearchRequestDTO dto = inv.getArgument(0);
            holder[0] = dto;
            return PageResult.of(List.<ScheduleResponseDTO>of(new ScheduleResponseDTO()), 42, dto);
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ScheduleController(service)).build();

        mvc.perform(get("/schedule?page=2&size=5&depStation=서울"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.totalCount").value(42));

        assertEquals(2, holder[0].getSafePage());
        assertEquals(5, holder[0].getSafeSize());
        assertEquals("서울", holder[0].getDepStation());
    }

    @Test
    @DisplayName("ResWrapper.list(PageResult) 는 totalCount 에 전체 건수를 쓴다")
    void wrapperListFromPageResult() {
        PageParam p = new PageParam();
        ResWrapper<List<String>> res = ResWrapper.list(PageResult.of(List.of("a", "b"), 99, p));
        assertEquals(99L, res.getTotalCount());
        assertEquals(2, res.getData().size());
    }
}
