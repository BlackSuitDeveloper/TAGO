import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Counter } from 'k6/metrics';

// ============================================================================
// 시나리오 D (현실 E2E 풀코스): 총 N명이 단일 열차 대기열에 진입 -> 서버 규칙대로 통과 -> 4가지 행동 패턴 분기
//   - 패턴 1 (20%): 인기 좌석(1호차 1A~1D) 동시 경합 -> 승자 결제(더블클릭 멱등성 포함), 패자(409) 퇴장
//   - 패턴 2 (50%): 일반 좌석 선점 -> 바로 결제 (90% 승인+일부 더블클릭 / 10% FAIL 거절)
//   - 패턴 3 (15%): 좌석 선점(결제 페이지 진입) 후 방치 -> leave 미호출, TTL 만료 회수 유도
//   - 패턴 4 (15%): 좌석 선점(결제 페이지 진입) 후 1~3초 뒤 브라우저 종료(leave 호출, 즉시 반납)
// ============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:80';
const JWT_SECRET = __ENV.JWT_SECRET || '';
const VUS = Number(__ENV.VUS || '200');
const MAX_POLL_SECONDS = Number(__ENV.MAX_POLL_SECONDS || String(Math.max(90, Math.ceil(VUS / 3))));

// 패턴별 집계 지표
const p1RaceWin = new Counter('p1_race_win');
const p1RaceConflict = new Counter('p1_race_conflict');
const p2PayApproved = new Counter('p2_pay_approved');
const p2PayReplay = new Counter('p2_pay_replay');
const p2PayDeclined = new Counter('p2_pay_declined');
const p3HoldAbandoned = new Counter('p3_hold_abandoned_for_ttl');
const p4BrowserClosed = new Counter('p4_browser_closed_leave');
const serverErrors5xx = new Counter('e2e_5xx_errors');

export const options = {
  scenarios: {
    realistic_e2e_flow: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '5m',
    },
  },
  thresholds: {
    e2e_5xx_errors: ['count==0'],
    p2_pay_approved: ['count>=1'],
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

// VU 번호별 충돌 없는 고유 좌석 배정 (5~18호차 일반실, 1A~14D = 최대 784석)
function uniqueSeatForVu(vu) {
  const cols = ['A', 'B', 'C', 'D'];
  const idx = (vu - 1) % 784;
  const carNo = 5 + Math.floor(idx / 56);
  const row = 1 + Math.floor((idx % 56) / 4);
  const col = cols[idx % 4];
  return { carNo, seatNo: `${row}${col}` };
}

// 인기 좌석 2개(1호차 1A, 1B)에 패턴 1 인원이 집중 경합
function hotSeatForVu(vu) {
  return { carNo: 1, seatNo: vu % 2 === 0 ? '1A' : '1B' };
}

// 행동 패턴 분류 (20% 경합 / 50% 즉시결제 / 15% 방치TTL / 15% 브라우저종료)
function classifyPattern(vu) {
  const bucket = vu % 20;
  if (bucket < 4) return 1;       // 0,1,2,3   -> 20%: 인기 좌석 경합
  if (bucket < 14) return 2;      // 4..13     -> 50%: 일반 선점 후 바로 결제
  if (bucket < 17) return 3;      // 14,15,16  -> 15%: 결제창 방치 (TTL 만료 유도)
  return 4;                       // 17,18,19  -> 15%: 결제창 진입 후 브라우저 종료(leave)
}

export function setup() {
  let scheduleSeq = __ENV.SCHEDULE_SEQ ? Number(__ENV.SCHEDULE_SEQ) : null;
  const schedRes = http.get(`${BASE_URL}/api/v1/schedule?upcoming=true&size=5`);
  const list = schedRes.json().data || [];
  if (list.length === 0) {
    throw new Error('예매 가능한 운행 일정이 없습니다.');
  }
  const picked = scheduleSeq
    ? (list.find((s) => s.scheduleSeq === scheduleSeq) || list[list.length - 1])
    : list[list.length - 1];

  scheduleSeq = picked.scheduleSeq;
  const unitPrice = (picked.standardPrice && picked.standardPrice > 0) ? picked.standardPrice : 54400;

  console.log(
    `[SETUP 완료] 현실 E2E 시나리오 D: 총 ${VUS}명 -> 일정 ${scheduleSeq} (운임 ${unitPrice}원) | ` +
    `패턴1(경합 20%) / 패턴2(결제 50%) / 패턴3(방치TTL 15%) / 패턴4(이탈 15%)`
  );
  return { scheduleSeq, unitPrice };
}

export default function (data) {
  const memberId = `k6_dummy_${__VU}`;
  const jwt = createJwt(memberId);
  const authHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
  };

  // ==========================================================================
  // 공통 0단계: 현실적인 유입 분산 (패턴1 인기좌석 경합은 초반 0~2초 집중, 나머지는 0~10초 분산 유입)
  // ==========================================================================
  const pattern = classifyPattern(__VU);
  if (pattern === 1) {
    sleep(Math.random() * 2);
  } else {
    sleep(Math.random() * 10);
  }

  // ==========================================================================
  // 공통 1단계: 전원 대기열 진입 -> 서버 배치 규칙대로 입장 허가 대기
  // ==========================================================================
  const qRes = http.post(
    `${BASE_URL}/api/v1/queue`,
    JSON.stringify({ scheduleSeq: data.scheduleSeq }),
    { headers: authHeaders, tags: { name: '1. POST /api/v1/queue' } }
  );
  if (qRes.status !== 201) {
    if (qRes.status >= 500) serverErrors5xx.add(1);
    return;
  }

  const queueToken = qRes.json().data.queueToken;
  let admitted = qRes.json().data.isAvailable === true;

  for (let i = 0; i < MAX_POLL_SECONDS && !admitted; i++) {
    sleep(1);
    const rankRes = http.get(`${BASE_URL}/api/v1/queue/${queueToken}`, {
      headers: authHeaders,
      tags: { name: '2. GET /api/v1/queue/:token' },
    });
    if (rankRes.status >= 500) serverErrors5xx.add(1);
    if (rankRes.status === 200 && rankRes.json().data.isAvailable === true) {
      admitted = true;
    }
  }
  if (!admitted) return;

  const holdHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
    'X-Queue-Token': queueToken,
  };

  // ==========================================================================
  // 공통 2단계: 좌석 선택 화면 체류 (인기좌석 경합은 0.5~1.5초, 일반 유저는 2~5초 좌석 탐색)
  // ==========================================================================
  if (pattern === 1) {
    sleep(0.5 + Math.random());
  } else {
    sleep(2 + Math.random() * 3);
  }

  // ==========================================================================
  // 패턴 1 (20%): 인기 좌석(1호차 1A/1B) 동시 경합
  // ==========================================================================
  if (pattern === 1) {
    const seat = hotSeatForVu(__VU);
    const holdRes = http.post(
      `${BASE_URL}/api/v1/reservation`,
      JSON.stringify({ scheduleSeq: data.scheduleSeq, seats: [seat] }),
      { headers: holdHeaders, tags: { name: '3-P1. POST /api/v1/reservation (hot seat)' } }
    );

    if (holdRes.status === 201) {
      p1RaceWin.add(1);
      sleep(2 + Math.random() * 3); // 결제 정보 입력 (2~5초)
      const holdTotal = (holdRes.json().data && holdRes.json().data.totalPrice) || data.unitPrice;
      const idemKey = `k6-idem-p1-${data.scheduleSeq}-${__VU}-${Date.now()}`;
      const payRes = http.post(
        `${BASE_URL}/api/v1/payment`,
        JSON.stringify({ payMethod: 'CARD', totalPrice: holdTotal, scheduleSeq: data.scheduleSeq }),
        { headers: { ...authHeaders, 'Idempotency-Key': idemKey }, tags: { name: '4-P1. POST /api/v1/payment' } }
      );
      if (payRes.status >= 500) serverErrors5xx.add(1);
      else if (payRes.status === 200) p2PayApproved.add(1);
    } else if (holdRes.status === 409) {
      p1RaceConflict.add(1);
      sleep(1 + Math.random() * 2); // 실패 메시지 확인 후 이탈
      http.post(`${BASE_URL}/api/v1/reservation/leave?scheduleSeq=${data.scheduleSeq}`, null, {
        headers: { Authorization: `Bearer ${jwt}` },
        tags: { name: '4-P1. POST /api/v1/reservation/leave (conflict)' },
      });
    } else if (holdRes.status >= 500) {
      serverErrors5xx.add(1);
    }

    check(holdRes, { '[P1] hot seat 201 or 409': (r) => r.status === 201 || r.status === 409 });
    return;
  }

  // ==========================================================================
  // 패턴 2, 3, 4 공통: 고유 빈 좌석 선점 (결제 페이지 진입)
  // ==========================================================================
  const seat = uniqueSeatForVu(__VU);
  const holdRes = http.post(
    `${BASE_URL}/api/v1/reservation`,
    JSON.stringify({ scheduleSeq: data.scheduleSeq, seats: [seat] }),
    { headers: holdHeaders, tags: { name: `3-P${pattern}. POST /api/v1/reservation` } }
  );
  if (holdRes.status !== 201) {
    if (holdRes.status >= 500) serverErrors5xx.add(1);
    return;
  }

  // ==========================================================================
  // 패턴 2 (50%): 자리 싸움 없이 바로 결제 후 통과 (3~7초 카드 입력 + 일부 더블클릭 + 일부 FAIL)
  // ==========================================================================
  if (pattern === 2) {
    sleep(3 + Math.random() * 4); // 결제 정보 입력 체류 (3~7초)
    const shouldFail = (__VU % 10 === 4); // 패턴 2 중 ~10% 결제 거절(FAIL)
    const holdTotal = (holdRes.json().data && holdRes.json().data.totalPrice) || data.unitPrice;
    const idemKey = `k6-idem-p2-${data.scheduleSeq}-${__VU}-${Date.now()}`;
    const payPayload = {
      payMethod: 'CARD',
      totalPrice: holdTotal,
      scheduleSeq: data.scheduleSeq,
    };
    if (shouldFail) payPayload.mockResult = 'FAIL';

    const payHeaders = { ...authHeaders, 'Idempotency-Key': idemKey };
    const payRes = http.post(`${BASE_URL}/api/v1/payment`, JSON.stringify(payPayload), {
      headers: payHeaders,
      tags: { name: '4-P2. POST /api/v1/payment' },
    });

    if (payRes.status >= 500) {
      serverErrors5xx.add(1);
      return;
    }

    if (shouldFail) {
      if (payRes.status >= 400 && payRes.status < 500) p2PayDeclined.add(1);
      http.post(`${BASE_URL}/api/v1/reservation/leave?scheduleSeq=${data.scheduleSeq}`, null, {
        headers: { Authorization: `Bearer ${jwt}` },
      });
      return;
    }

    if (check(payRes, { '[P2] payment approved (200)': (r) => r.status === 200 })) {
      p2PayApproved.add(1);
      // 패턴 2 중 20%는 결제 버튼 더블클릭(동일 Idempotency-Key 재전송) 시뮬레이션
      if (__VU % 5 === 0) {
        const replayRes = http.post(`${BASE_URL}/api/v1/payment`, JSON.stringify(payPayload), {
          headers: payHeaders,
          tags: { name: '4-P2. POST /api/v1/payment (replay)' },
        });
        if (replayRes.status >= 500) serverErrors5xx.add(1);
        else if (replayRes.status === 200) p2PayReplay.add(1);
      }
    }
    return;
  }

  // ==========================================================================
  // 패턴 3 (15%): 결제 페이지 진입 성공 후 방치 -> leave 안 부르고 종료 (5분 TTL 만료 회수 대상)
  // ==========================================================================
  if (pattern === 3) {
    p3HoldAbandoned.add(1);
    return;
  }

  // ==========================================================================
  // 패턴 4 (15%): 결제 페이지 진입 후 3~8초 고민하다 브라우저 종료 (sendBeacon -> leave 호출)
  // ==========================================================================
  sleep(3 + Math.random() * 5);
  const leaveRes = http.post(
    `${BASE_URL}/api/v1/reservation/leave?scheduleSeq=${data.scheduleSeq}`,
    null,
    { headers: { Authorization: `Bearer ${jwt}` }, tags: { name: '4-P4. POST /api/v1/reservation/leave (close)' } }
  );
  if (leaveRes.status >= 500) serverErrors5xx.add(1);
  else if (leaveRes.status === 200) p4BrowserClosed.add(1);
}
