package com.kedu.tago.sync.dao;

import com.kedu.tago.sync.dto.ScheduleInsertDTO;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 운행 일정(schedule) 동기화 DAO (SQL: mapper/schedule_sync_mapper.xml) */
@Repository
@RequiredArgsConstructor
public class ScheduleSyncDAO {

    private final SqlSession sqlSession;

    /** 동일 열차/출발역/도착역/출발시각 일정 존재 여부 (멱등성 보장) */
    public boolean existsSchedule(int trainno, String depNodeId, String arrNodeId, LocalDateTime depTime) {
        Map<String, Object> params = new HashMap<>();
        params.put("trainno", trainno);
        params.put("depNodeId", depNodeId);
        params.put("arrNodeId", arrNodeId);
        params.put("depTime", depTime);

        Boolean exists = sqlSession.selectOne("ScheduleSync.existsSchedule", params);
        return Boolean.TRUE.equals(exists);
    }

    /** 일정 INSERT 후 생성된 schedule_seq 반환 */
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

        sqlSession.insert("ScheduleSync.insertSchedule", dto);
        return dto.getScheduleSeq();
    }

}
