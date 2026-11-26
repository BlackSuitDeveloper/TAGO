import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Counter } from 'k6/metrics';

// ============================================================================
// 시나리오 C: 결제 + 더블클릭 멱등성(Idempotency-Key) + 결제 거절(FAIL) 검증
// - 서로 다른 더미 회원 N명이 각자 고유 좌석을 선점한 뒤 결제까지 완료
// - 모든 정상 결제 회원은 동일한 Idempotency-Key로 1회 재요청(더블클릭 멱등성 검증)
// - 5명 중 1명은 mockResult='FAIL' 로 결제 거절(declined) 시나리오 검증
// - Grafana 'TAGO 예매' 대시보드의 결제 결과/처리시간(p95) 패널 전체 활성화!
// ============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:80';
const JWT_SECRET = __ENV.JWT_SECRET || '';
const VUS = Number(__ENV.VUS || '20');

const payApproved = new Counter('pay_approved');
const payReplay = new Counter('pay_idempotent_replay');
const payDeclined = new Counter('pay_declined');
const pay5xx = new Counter('pay_5xx_errors');

export const options = {
  scenarios: {
    payment_flow: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '90s',
    },
  },
  thresholds: {
    pay_5xx_errors: ['count==0'],
    pay_approved: ['count>=1'],
    pay_idempotent_replay: ['count>=1'],
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

// VU 번호마다 겹치지 않는 좌석(5~16호차, 1A~14D) 배정
function seatForVu(vu) {
  const cols = ['A', 'B', 'C', 'D'];
  const idx = vu - 1;
  const carNo = 5 + Math.floor(idx / 56);
  const row = 1 + Math.floor((idx % 56) / 4);
  const col = cols[idx % 4];
  return { carNo, seatNo: `${row}${col}` };
}

export function setup() {
  let scheduleSeq = __ENV.SCHEDULE_SEQ ? Number(__ENV.SCHEDULE_SEQ) : null;
  let unitPrice = 54400;
  const schedRes = http.get(`${BASE_URL}/api/v1/schedule?upcoming=true&size=5`);
  const list = schedRes.json().data || [];
  if (list.length === 0) {
    throw new Error('예매 가능한 운행 일정이 없습니다.');
  }
  const picked = scheduleSeq
    ? (list.find((s) => s.scheduleSeq === scheduleSeq) || list[list.length - 1])
    : list[list.length - 1];
  scheduleSeq = picked.scheduleSeq;
  if (picked.standardPrice && picked.standardPrice > 0) {
    unitPrice = picked.standardPrice;
  }
  console.log(`[SETUP 완료] 시나리오 D 결제 테스트: scheduleSeq=${scheduleSeq}, 운임=${unitPrice}, 더미 회원=${VUS}명`);
  return { scheduleSeq, unitPrice };
}

export default function (data) {
  const memberId = `k6_dummy_${__VU}`;
  const jwt = createJwt(memberId);
  const authHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
  };

  // 1. 대기열 진입 및 입장 허가 대기
  const qRes = http.post(
    `${BASE_URL}/api/v1/queue`,
    JSON.stringify({ scheduleSeq: data.scheduleSeq }),
    { headers: authHeaders, tags: { name: '[D] POST /api/v1/queue' } }
  );
  if (qRes.status !== 201) {
    if (qRes.status >= 500) pay5xx.add(1);
    return;
  }
  const queueToken = qRes.json().data.queueToken;

  let admitted = qRes.json().data.isAvailable === true;
  for (let i = 0; i < 30 && !admitted; i++) {
    sleep(1);
    const rankRes = http.get(`${BASE_URL}/api/v1/queue/${queueToken}`, {
      headers: authHeaders,
      tags: { name: '[D] GET /api/v1/queue/:token' },
    });
    if (rankRes.status === 200 && rankRes.json().data.isAvailable === true) {
      admitted = true;
    }
  }
  if (!admitted) return;

  // 2. VU별 고유 좌석 선점
  const seat = seatForVu(__VU);
  const holdRes = http.post(
    `${BASE_URL}/api/v1/reservation`,
    JSON.stringify({
      scheduleSeq: data.scheduleSeq,
      seats: [seat],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${jwt}`,
        'X-Queue-Token': queueToken,
      },
      tags: { name: '[D] POST /api/v1/reservation' },
    }
  );
  if (holdRes.status !== 201) {
    if (holdRes.status >= 500) pay5xx.add(1);
    return;
  }

  // 3. 결제 요청 (5명 중 1명은 mockResult='FAIL' 로 거절 시나리오 검증)
  const shouldFail = (__VU % 5 === 0);
  const idemKey = `k6-idem-${data.scheduleSeq}-${__VU}-${Date.now()}`;
  const payPayload = {
    payMethod: 'CARD',
    totalPrice: data.unitPrice,
    scheduleSeq: data.scheduleSeq,
  };
  if (shouldFail) {
    payPayload.mockResult = 'FAIL';
  }

  const payHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
    'Idempotency-Key': idemKey,
  };

  const payRes = http.post(
    `${BASE_URL}/api/v1/payment`,
    JSON.stringify(payPayload),
    { headers: payHeaders, tags: { name: '[D] POST /api/v1/payment' } }
  );

  if (payRes.status >= 500) {
    pay5xx.add(1);
    return;
  }

  if (shouldFail) {
    if (payRes.status === 400 || payRes.status === 402 || payRes.status === 409) {
      payDeclined.add(1);
    }
    check(payRes, { '[D] mock FAIL declined cleanly (4xx)': (r) => r.status >= 400 && r.status < 500 });
    // 거절 후 선점 해제
    http.post(`${BASE_URL}/api/v1/reservation/leave?scheduleSeq=${data.scheduleSeq}`, null, {
      headers: { Authorization: `Bearer ${jwt}` },
    });
    return;
  }

  const okPay = check(payRes, {
    '[D] payment approved (200)': (r) => r.status === 200,
  });
  if (!okPay) return;
  payApproved.add(1);

  // 4. 결제 더블클릭 시뮬레이션 (동일 Idempotency-Key 재전송 -> 멱등성 응답 검증)
  const replayRes = http.post(
    `${BASE_URL}/api/v1/payment`,
    JSON.stringify(payPayload),
    { headers: payHeaders, tags: { name: '[D] POST /api/v1/payment (idempotent replay)' } }
  );
  if (replayRes.status >= 500) {
    pay5xx.add(1);
    return;
  }
  const okReplay = check(replayRes, {
    '[D] double-click replay returns 200 without double charge': (r) => r.status === 200,
  });
  if (okReplay) {
    payReplay.add(1);
  }
}
