package com.kedu.tago.sync.dao;

import com.kedu.tago.sync.dto.ScheduleInsertDTO;
import com.kedu.tago.sync.service.SeatTemplateGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 동적 운행 일정(schedule) 및 회차별 좌석(seat) MyBatis 기반 DB 접근 객체 (DAO).
 * SQL문은 src/main/resources/mapper/schedule_sync_mapper.xml 에 정의되어 있습니다.
 */
@Repository
@RequiredArgsConstructor
public class ScheduleSyncDAO {

    private final SqlSession mybatis;

    /**
     * 동일 열차/출발역/도착역/출발시각 일정 존재 여부 확인 (멱등성 보장)
     */
    public boolean existsSchedule(int trainno, String depNodeId, String arrNodeId, LocalDateTime depTime) {
        Map<String, Object> params = new HashMap<>();
        params.put("trainno", trainno);
        params.put("depNodeId", depNodeId);
        params.put("arrNodeId", arrNodeId);
        params.put("depTime", depTime);

        Boolean exists = mybatis.selectOne("ScheduleSync.existsSchedule", params);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 신규 schedule INSERT 및 useGeneratedKeys로 생성된 schedule_seq 반환
     */
    public Long insertScheduleAndReturnSeq(
            int trainno,
            String depNodeId,
            String arrNodeId,
            LocalDateTime depTime,
            LocalDateTime arrTime,
            LocalDateTime bookingOpenAt,
            int firstPrice,
            int standardPrice) {

        ScheduleInsertDTO dto = ScheduleInsertDTO.builder()
                .trainno(trainno)
                .depNodeId(depNodeId)
                .arrNodeId(arrNodeId)
                .depTime(depTime)
                .arrTime(arrTime)
                .bookingOpenAt(bookingOpenAt)
                .firstPrice(firstPrice)
                .standardPrice(standardPrice)
                .build();

        mybatis.insert("ScheduleSync.insertSchedule", dto);
        return dto.getScheduleSeq();
    }

    /**
     * 신규 생성된 schedule_seq에 대해 1~18호차 총 935석(모두 STANDARD) 좌석을 MyBatis <foreach>로 일괄 적재
     */
    public int batchInsertSeats(long scheduleSeq, List<SeatTemplateGenerator.SeatTemplateItem> template) {
        if (template == null || template.isEmpty()) {
            return 0;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("scheduleSeq", scheduleSeq);
        params.put("seats", template);

        mybatis.insert("ScheduleSync.batchInsertSeats", params);
        return template.size();
    }
}
