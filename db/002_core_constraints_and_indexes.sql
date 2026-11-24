-- DB-1 / DB-3: 핵심 제약조건(이중 예매 방지 UNIQUE), Sparse 좌석 스키마, 조회 인덱스 고정 (여러 번 실행해도 안전)

-- 1. seat: Sparse 좌석 구조(status 제거) + 이중 예매 방지 UNIQUE 제약 + 일정별 집계 인덱스
ALTER TABLE seat DROP COLUMN IF EXISTS status;
CREATE UNIQUE INDEX IF NOT EXISTS uq_seat_schedule_car_seat
    ON seat (schedule_seq, car_no, seat_no);
CREATE INDEX IF NOT EXISTS idx_seat_schedule_seq
    ON seat (schedule_seq);

-- 2. reservation: 취소 시 점유 좌석(seat) 행 삭제를 위한 NULL 허용 + 호차/좌석 스냅샷 컬럼 + 만료 회수/내 예매 조회 인덱스
ALTER TABLE reservation ALTER COLUMN seat_seq DROP NOT NULL;
ALTER TABLE reservation ADD COLUMN IF NOT EXISTS car_no INTEGER;
ALTER TABLE reservation ADD COLUMN IF NOT EXISTS seat_no VARCHAR(10);
ALTER TABLE reservation ADD COLUMN IF NOT EXISTS seat_grade VARCHAR(20);

UPDATE reservation r
SET car_no = s.car_no,
    seat_no = s.seat_no,
    seat_grade = s.seat_grade
FROM seat s
WHERE r.seat_seq = s.seat_seq
  AND (r.car_no IS NULL OR r.seat_no IS NULL);

CREATE INDEX IF NOT EXISTS idx_reservation_status_expires
    ON reservation (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_reservation_member_reserved
    ON reservation (member_id, reserved_at DESC);

-- 3. payment: 예약 기준 조인 인덱스
CREATE INDEX IF NOT EXISTS idx_payment_reservation_seq
    ON payment (reservation_seq);

-- 4. schedule: 시간표 조회 및 동기화 중복 확인 인덱스
CREATE INDEX IF NOT EXISTS idx_schedule_departmental
    ON schedule (depplandtime);
CREATE INDEX IF NOT EXISTS idx_schedule_sync_lookup
    ON schedule (trainno, depplacename, arrplacename, depplandtime);

-- 5. wishlist: 동일 회원·일정 중복 찜 방지 UNIQUE 인덱스
CREATE UNIQUE INDEX IF NOT EXISTS uq_wishlist_member_schedule
    ON wishlist (member_id, schedule_seq);
