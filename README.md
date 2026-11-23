
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
| Auth / External | JWT (java-jwt), BCrypt, Google Cloud Storage, 공공데이터포털 TAGO API |
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


