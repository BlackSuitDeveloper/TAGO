import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Counter } from 'k6/metrics';

// ============================================================================
// 시나리오 B: 서로 다른 더미 회원 N명(k6_dummy_1..N) 동일 좌석 동시 선점 경합
// 검증 목표:
//   1) 각 VU가 서로 다른 회원(memberId) + 각자의 queueToken으로 대기열 통과
//   2) N명이 동시에 같은 좌석(1호차 1A) 선점 요청 -> 정확히 1명만 201 성공
//   3) 나머지 N-1명은 409 Conflict(SEAT_ALREADY_TAKEN)로 안전 차단
//   4) 5xx 서버 에러 0건
// ============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:80';
const JWT_SECRET = __ENV.JWT_SECRET || '';
const CAR_NO = Number(__ENV.CAR_NO || '1');
const SEAT_NO = __ENV.SEAT_NO || '1A';
const VUS = Number(__ENV.VUS || '50');

const holdSuccess = new Counter('seat_hold_success');
const holdConflict = new Counter('seat_hold_conflict');
const holdServerError = new Counter('seat_hold_5xx');

export const options = {
  scenarios: {
    race_same_seat: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '90s',
    },
  },
  thresholds: {
    seat_hold_success: ['count==1'],
    seat_hold_conflict: [`count==${VUS - 1}`],
    seat_hold_5xx: ['count==0'],
  },
};

function createJwt(memberId) {
  const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
  const now = Math.floor(Date.now() / 1000);
  const payload = encoding.b64encode(
    JSON.stringify({ sub: memberId, role: 'USER', iat: now, exp: now + 3600 }),
    'rawurl'
  );
  const signingInput = `${header}.${payload}`;
  const signature = crypto.hmac('sha256', JWT_SECRET, signingInput, 'base64rawurl');
  return `${signingInput}.${signature}`;
}

export function setup() {
  let scheduleSeq = __ENV.SCHEDULE_SEQ ? Number(__ENV.SCHEDULE_SEQ) : null;
  if (!scheduleSeq) {
    const schedRes = http.get(`${BASE_URL}/api/v1/schedule?upcoming=true&size=5`);
    const list = schedRes.json().data || [];
    if (list.length === 0) {
      throw new Error('예매 가능한 운행 일정이 없습니다.');
    }
    scheduleSeq = list[list.length - 1].scheduleSeq;
  }
  console.log(`[SETUP 완료] scheduleSeq=${scheduleSeq}, 좌석=${CAR_NO}호차 ${SEAT_NO}, 서로 다른 더미 회원=${VUS}명`);
  return { scheduleSeq };
}

export default function (data) {
  const memberId = `k6_dummy_${__VU}`;
  const jwt = createJwt(memberId);
  const authHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
  };

  // 1. 각자 대기열 진입 후 입장 허가 대기
  const qRes = http.post(
    `${BASE_URL}/api/v1/queue`,
    JSON.stringify({ scheduleSeq: data.scheduleSeq }),
    { headers: authHeaders, tags: { name: 'POST /api/v1/queue' } }
  );
  if (qRes.status !== 201) {
    if (qRes.status >= 500) holdServerError.add(1);
    return;
  }
  const queueToken = qRes.json().data.queueToken;

  let admitted = qRes.json().data.isAvailable === true;
  for (let i = 0; i < 30 && !admitted; i++) {
    sleep(1);
    const rankRes = http.get(`${BASE_URL}/api/v1/queue/${queueToken}`, {
      headers: authHeaders,
      tags: { name: 'GET /api/v1/queue/:token' },
    });
    if (rankRes.status === 200 && rankRes.json().data.isAvailable === true) {
      admitted = true;
    }
  }
  if (!admitted) return;

  // 2. 동일 좌석 선점 경합 (POST /api/v1/reservation)
  const res = http.post(
    `${BASE_URL}/api/v1/reservation`,
    JSON.stringify({
      scheduleSeq: data.scheduleSeq,
      seats: [{ carNo: CAR_NO, seatNo: SEAT_NO }],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${jwt}`,
        'X-Queue-Token': queueToken,
      },
      tags: { name: 'POST /api/v1/reservation (same seat race)' },
    }
  );

  if (res.status === 201) holdSuccess.add(1);
  else if (res.status === 409) holdConflict.add(1);
  else if (res.status >= 500) holdServerError.add(1);

  check(res, {
    'status is 201 (winner) or 409 (blocked)': (r) => r.status === 201 || r.status === 409,
  });

  // 3. 테스트 종료 후 선점 및 대기열 슬롯 반납
  sleep(1);
  http.post(
    `${BASE_URL}/api/v1/reservation/leave?scheduleSeq=${data.scheduleSeq}`,
    null,
    { headers: { Authorization: `Bearer ${jwt}` }, tags: { name: 'POST /api/v1/reservation/leave' } }
  );
}
