import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Counter } from 'k6/metrics';

// ============================================================================
// 시나리오 A: 대기열(Queue) N명 동시 진입 -> 입장 대기(폴링) -> 슬롯 반납(퇴장)
// 검증 목표:
//   1) 동시 입장(active)이 MAX_ACTIVE(200) 상한을 초과하지 않음
//   2) 입장 완료 후 leave 호출로 인원이 깔끔하게 빠져나가 0으로 복귀
//   3) 5xx 서버 에러 0건
// ============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:80';
const SCHEDULE_SEQ = Number(__ENV.SCHEDULE_SEQ || '999999');
const JWT_SECRET = __ENV.JWT_SECRET || '';
const VUS = Number(__ENV.VUS || '300');
const MAX_POLL_SECONDS = Number(__ENV.MAX_POLL_SECONDS || '30');

const admittedCount = new Counter('queue_admitted');
const serverErrors = new Counter('queue_5xx_errors');

export const options = {
  scenarios: {
    spike_queue_users: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '90s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    queue_5xx_errors: ['count==0'],
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

export default function () {
  const memberId = `k6_q_user_${__VU}`;
  const jwt = createJwt(memberId);
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${jwt}`,
  };

  // 1. 대기열 진입 (POST /api/v1/queue)
  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue`,
    JSON.stringify({ scheduleSeq: SCHEDULE_SEQ }),
    { headers, tags: { name: 'POST /api/v1/queue' } }
  );
  if (enterRes.status >= 500) serverErrors.add(1);

  let body = null;
  try { body = enterRes.json(); } catch (_) {}

  const okEnter = check(enterRes, {
    'enter status is 201': (r) => r.status === 201,
    'enter has queueToken': () => !!(body && body.success && body.data && body.data.queueToken),
  });
  if (!okEnter) return;

  const queueToken = body.data.queueToken;
  let admitted = body.data.isAvailable === true;

  // 2. 입장 허가될 때까지 폴링 (최대 MAX_POLL_SECONDS 초)
  for (let i = 0; i < MAX_POLL_SECONDS && !admitted; i++) {
    sleep(1);
    const pollRes = http.get(`${BASE_URL}/api/v1/queue/${queueToken}`, {
      headers,
      tags: { name: 'GET /api/v1/queue/:token' },
    });
    if (pollRes.status >= 500) serverErrors.add(1);
    check(pollRes, { 'poll status is 200': (r) => r.status === 200 });

    try {
      const pBody = pollRes.json();
      if (pBody && pBody.data && pBody.data.isAvailable === true) {
        admitted = true;
      }
    } catch (_) {}
  }

  if (admitted) {
    admittedCount.add(1);
    // Grafana에서 입장 인원(active) 그래프가 보이도록 1~2초 체류 후 퇴장
    sleep(1 + Math.random());
  }

  // 3. 대기열 슬롯 반납 / 퇴장 (POST /api/v1/reservation/leave)
  const leaveRes = http.post(
    `${BASE_URL}/api/v1/reservation/leave?scheduleSeq=${SCHEDULE_SEQ}`,
    null,
    { headers, tags: { name: 'POST /api/v1/reservation/leave' } }
  );
  if (leaveRes.status >= 500) serverErrors.add(1);
  check(leaveRes, { 'leave status is 200': (r) => r.status === 200 });
}
