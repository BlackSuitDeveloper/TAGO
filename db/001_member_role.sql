-- 회원 권한(ROLE) 컬럼 추가. 서버를 배포하기 전에 한 번 실행한다. (여러 번 실행해도 안전)
-- 기존 회원은 모두 USER 가 된다.
ALTER TABLE member ADD COLUMN IF NOT EXISTS role VARCHAR(10) NOT NULL DEFAULT 'USER';

ALTER TABLE member DROP CONSTRAINT IF EXISTS chk_member_role;
ALTER TABLE member ADD CONSTRAINT chk_member_role CHECK (role IN ('ADMIN', 'USER'));

-- 관리자 지정(관리자로 쓸 회원 아이디로 바꿔서 실행). 이미 로그인 중이던 토큰은 만료(기본 1시간) 후 새 권한이 적용된다.
-- UPDATE member SET role = 'ADMIN' WHERE member_id = '관리자아이디';

-- 되돌리기
-- ALTER TABLE member DROP CONSTRAINT chk_member_role;
-- ALTER TABLE member DROP COLUMN role;
