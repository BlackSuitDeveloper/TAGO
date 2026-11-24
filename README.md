
# 🚄 TAGO — 실시간 열차 예매 서비스

## 📖 1. 프로젝트 개요

- **프로젝트명**: TAGO (실시간 열차 예매 웹 서비스)
- **개발 기간**: `2026.11.16~2026.11.27`

### 🎯 기획 배경 및 목표

- 공공데이터포털(TAGO) 열차 운행 정보를 연동한 실제 노선 및 스케줄 기반 예매 서비스 제공
- 다수의 사용자가 동일 좌석에 동시 요청을 보내는 상황에서 **정확히 1명만 좌석을 선점하는 동시성 제어** 구현
- Redis 기반 대기열 시스템을 통한 사용자 요청 관리 및 트래픽 제어
- 실시간 열차 예매 환경을 고려한 안정적인 예약 및 좌석 관리 시스템 구축
- 동시성 제어, 멱등성 보장, 데이터의 정합성이 목표

---

## 🛠️ 2. 핵심 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4.0, MyBatis|
| Frontend | React 19, Vite 8, Zustand, React Router DOM, Axios |
| Database / Cache | PostgreSQL, Redis (Docker) |
| Auth / External | JWT (java-jwt), BCrypt, 공공데이터포털 TAGO API |
| Infra / Test | Docker, AWS EC2, JUnit 5 |

---

## 🏗️ 3. 시스템 아키텍처

### 시스템 구성도

<img width="1190" height="662" alt="image" src="https://github.com/user-attachments/assets/0fc560d9-b6ca-4a66-bf28-172fd4210ff1" />


### 인프라 구성

| 구분 | 구성 |
|---|---|
| Application Server | AWS EC2 |
| Backend | Spring Boot |
| Frontend | React + Vite |
| Database | PostgreSQL (Docker)|
| Cache / Queue | Redis (Docker) |
| External API | 공공데이터포털 TAGO API |

---

### 📑4. API 문서

- **Base URL**: `http://{HOST}/api`
- **인증 방식**: JWT 기반 인증
- **인증 헤더**: `Authorization: Bearer <JWT_TOKEN>`

#### 📦 공통 응답 형식

모든 API(실시간 좌석 스트림 `/seat/stream` 제외)는 아래 세 가지 중 하나로 응답하며, 값이 없는 필드는 생략된다.

| 구분 | 형식 | 설명 |
|---|---|---|
| 일반 | `{ "success": true, "data": {...}, "message": "..." }` | `data`, `message` 는 있을 때만 |
| 목록 | `{ "success": true, "data": [...], "totalCount": 120 }` | `totalCount` 는 조건에 맞는 전체 건수 |
| 에러 | `{ "success": false, "status": 409, "code": 4003, "message": "..." }` | `status` 는 HTTP 상태 코드, `code` 는 아래 에러 코드 |

- 서버: 성공은 `ResWrapper.ok / done / list`, 에러는 `throw new ApiException(ErrorCode.XXX)` 를 던지면 `GlobalExceptionHandler` 가 위 형식으로 바꾼다. 상황별 문구가 필요하면 `new ApiException(ErrorCode.XXX, "문구")`.
- 페이징: 목록 검색 요청 DTO 가 `PageParam`(`page`, `size`)을 상속하고, 서비스는 `PageResult` 를 반환하고, 컨트롤러는 `ResWrapper.list(pageResult)` 로 응답한다. `page` 는 1부터, `size` 기본 10 / 최대 100.

#### 🔢 에러 코드

번호는 종류별 대역으로 나눈다. 전체 목록은 `ErrorCode.java` 에 있고, 새 에러는 해당 대역에 추가한다.

| 대역 | 종류 | 예 |
|---|---|---|
| 1000번대 | 공통 (요청 형식, 경로, 데이터 충돌) | 1000 요청 형식 오류, 1002 경로 없음 |
| 2000번대 | 인증 / 인가 | 2000 로그인 필요, 2001 토큰 오류, 2004 관리자 전용, 2005 본인 것만 가능 |
| 3000번대 | 회원 / 이메일 인증 | 3010 인증번호 불일치, 3020 비밀번호 길이 |
| 4000번대 | 운행 / 좌석 | 4000 운행 일정 없음, 4003 이미 선택된 좌석 |
| 5000번대 | 예매 / 결제 | 5000 예매 내역 없음, 5011 선점 시간 만료 |
| 9000번대 | 서버 | 9000 내부 오류 |

#### 🔐 인증 / 권한 규칙

규칙은 `AccessPolicy`(경로 기준)와 `AuthInterceptor`(토큰·권한 검사) 한 곳에서 처리한다.

- **권한(ROLE)**: 회원은 `USER`(기본) 또는 `ADMIN` 이다. `member.role` 컬럼에 저장되고, 로그인 시 JWT 의 `role` 에 담긴다. `ADMIN` 은 `USER` 가 할 수 있는 것을 모두 할 수 있다.
- **공개**(로그인 불필요, 메서드까지 일치): 회원가입 `POST /member`, 로그인, 아이디 중복 확인, `/auth/**`, 역 목록, 시간표 조회, 좌석 조회/스트림
- **그 밖의 모든 API**: 로그인 필요(`Authorization: Bearer`, USER 이상). 새 API 를 만들면 기본적으로 보호된다.
- **관리자 전용**: 컨트롤러(또는 메서드)에 `@RequireRole(Role.ADMIN)` 을 붙인다(예: `/admin/sync/**`). 경로가 `/admin/**` 이면 어노테이션이 없어도 ADMIN 이 필요하다. 권한이 부족하면 403(관리자 전용은 에러 코드 2004).
- **본인 것만**: 예매 취소와 결제는 서비스에서 예약 소유자를 확인한다(2005).

권한을 쓰려면 DB 에 컬럼을 한 번 추가해야 한다(서버 배포 전에 실행).

```sql
-- db/001_member_role.sql 실행 (기존 회원은 모두 USER)
-- 관리자 지정
UPDATE member SET role = 'ADMIN' WHERE member_id = '관리자아이디';
```

- 가입 요청으로는 권한을 지정할 수 없다(항상 `USER`). 권한을 바꾸면 이미 발급된 토큰이 만료된 뒤(기본 1시간) 적용된다.
- 로그인 응답과 `GET /member/mypage` 응답에 `role` 이 포함된다.

#### 👤 회원 (Member)

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| POST | `/member` | 회원가입 | ❌ |
| POST | `/member/login` | 로그인 (JWT 토큰 발급) | ❌ |
| GET | `/member/checkId` | 아이디 중복 체크 | ❌ |
| GET | `/member/mypage` | 내 정보 조회 | ✅ |
| PUT | `/member` | 회원 정보 수정 | ✅ |
| DELETE | `/member` | 회원 탈퇴 | ✅ |

#### ⏳ 대기열 (Queue)

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| POST | `/queue` | 대기열 진입 (queueToken 발급) | ✅ |
| GET | `/queue/{queueToken}` | 대기열 순번 조회 | ✅ |

#### 🎫 예매 (Reservation)

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| POST | `/reservation` | 좌석 선점 및 예매 생성 (HELD) | ✅ |
| GET | `/reservation/me` | 내 승차권 목록 조회 | ✅ |
| POST | `/reservation/{reservationId}/cancel` | 승차권 예매 취소 및 좌석 반환 | ✅ |



---

## 🔧 로컬 실행 환경 설정

1. PostgreSQL 과 Redis 를 준비한다.
2. 프로젝트 루트에 `.env` 를 만들고 값을 채운다. 필수 항목은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` 이고, 나머지는 `application.properties` 의 기본값을 쓴다(바꾸려면 같은 이름의 환경변수를 지정). `.env` 는 커밋하지 않는다.
3. `./gradlew bootRun` 으로 실행한다. 기본 포트는 80 이며 `SERVER_PORT` 로 바꿀 수 있다.
