// TAGO 예매 부하테스트: 1,000명 → 대기열(active 50) → 좌석 100석 선점(1~4석) → 결제
// 사용자 번호(VU)로 결과를 고정 배분: 80% 결제 성공, 10% 이탈(결제창 방치 → TTL 만료), 10% 결제 실패(모의 PG 거절)
// 로그인은 setup() 에서 미리 해 둔다 (실제 예매 오픈 때도 사람들은 미리 로그인해 두고 기다린다)
// 결제 성공 그룹 중 끝자리 2·7 은 "결제 버튼 더블클릭"(같은 멱등키로 2번 동시 요청)을 흉내 낸다
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080/api/v1';
const SCHEDULE = Number(__ENV.SCHEDULE || 183);
const USERS = Number(__ENV.USERS || 1000);
const PASSWORD = __ENV.PASSWORD || 'loadtest1234';

// 대상 좌석 100석: 1호차 56석 + 2호차 35석 + 3호차 앞 9석 (SeatTemplateGenerator 기준)
const POOL_CARS = [[1, 56], [2, 35], [3, 9]];
const POOL = [];
for (const [carNo, count] of POOL_CARS) {
  for (let i = 0; i < count; i++) POOL.push({ carNo, seatNo: `${Math.floor(i / 4) + 1}${'ABCD'[i % 4]}` });
}
const POOL_KEYS = new Set(POOL.map((s) => `${s.carNo}-${s.seatNo}`));

const seatsSold = new Counter('seats_sold');
const buyersPaid = new Counter('buyers_paid');
const buyersDeclined = new Counter('buyers_declined');
const buyersAbandoned = new Counter('buyers_abandoned');
const exitSoldOut = new Counter('exit_sold_out');
const exitGaveUp = new Counter('exit_gave_up');
const holdConflicts = new Counter('hold_conflicts_409');
const queueExpired = new Counter('queue_expired');
const unexpected = new Counter('unexpected_errors');
const doubleClicks = new Counter('double_click_users');
const queueWait = new Trend('queue_wait_ms', true);
const timeToHold = new Trend('time_to_hold_ms', true);

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 402, 409));

export const options = {
  scenarios: {
    users: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '20m' },
  },
  setupTimeout: '180s',
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  // API 별 응답시간 목표 (k6 요약에 API 별로 따로 표시된다)
  thresholds: {
    'http_req_duration{name:queue_enter}': ['p(95)<500'],
    'http_req_duration{name:seat_list}': ['p(95)<500'],
    'http_req_duration{name:hold}': ['p(95)<500'],
    'http_req_duration{name:payment}': ['p(95)<1000'],
  },
};

// 테스트 시작 전 1번: 1,000명 로그인 (50명씩 묶어서 동시에)
export function setup() {
  const tokens = [];
  for (let start = 1; start <= USERS; start += 50) {
    const batch = [];
    for (let vu = start; vu < Math.min(start + 50, USERS + 1); vu++) {
      batch.push(['POST', `${BASE}/auth/login`,
        JSON.stringify({ id: `lt${String(vu).padStart(4, '0')}`, pw: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }]);
    }
    for (const res of http.batch(batch)) {
      if (res.status !== 200) throw new Error(`setup 로그인 실패: ${res.status} ${res.body}`);
      tokens.push(json(res).data.token);
    }
  }
  return { tokens };
}

function json(res) {
  try { return res.json(); } catch (e) { return null; }
}

function fail(stage, res) {
  unexpected.add(1, { stage });
  console.warn(`[VU ${__VU}] ${stage} 실패 status=${res.status} body=${String(res.body).slice(0, 200)}`);
}

// 대상 100석의 현재 상태 조회
function readPool(headers) {
  const available = [];
  let confirmed = 0;
  for (const [carNo] of POOL_CARS) {
    const res = http.get(`${BASE}/seat?scheduleSeq=${SCHEDULE}&carNo=${carNo}`, { headers, tags: { name: 'seat_list' } });
    if (res.status === 403) return { expired: true };
    if (res.status !== 200) { fail('seat_list', res); return { error: true }; }
    for (const seat of json(res).data.seatList) {
      if (!POOL_KEYS.has(`${carNo}-${seat.seatNo}`)) continue;
      if (seat.status === 'AVAILABLE') available.push({ carNo, seatNo: seat.seatNo });
      else if (seat.status === 'CONFIRMED') confirmed++;
    }
  }
  return { available, soldOut: confirmed === POOL.length };
}

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

export default function (data) {
  const vu = __VU;
  const memberId = `lt${String(vu).padStart(4, '0')}`;
  const outcome = vu % 10 === 0 ? 'abandon' : vu % 10 === 1 ? 'fail' : 'pay';
  const want = ((vu * 7) % 4) + 1; // 1~4석

  sleep(Math.random() * 10); // 10초에 걸쳐 1,000명 도착

  // 1. 로그인은 setup() 에서 끝냄
  const headers = { Authorization: `Bearer ${data.tokens[vu - 1]}`, 'Content-Type': 'application/json' };

  // 2. 대기열 진입 → 입장까지 2초마다 폴링
  const t0 = Date.now();
  const enter = http.post(`${BASE}/queue`, JSON.stringify({ scheduleSeq: SCHEDULE }), { headers, tags: { name: 'queue_enter' } });
  if (enter.status !== 201) return fail('queue_enter', enter);
  const queueToken = json(enter).data.queueToken;
  let admitted = json(enter).data.isAvailable === true;
  while (!admitted) {
    if (Date.now() - t0 > 15 * 60 * 1000) { queueExpired.add(1); return; }
    sleep(2);
    const st = http.get(`${BASE}/queue/${queueToken}`, { headers, tags: { name: 'queue_status' } });
    if (st.status !== 200) return fail('queue_status', st);
    admitted = json(st).data.isAvailable === true;
  }
  queueWait.add(Date.now() - t0);
  headers['X-Queue-Token'] = queueToken;

  // 3. 좌석 선택 → 선점 (409면 다른 좌석으로 재시도, 매진이면 종료)
  const t1 = Date.now();
  let held = 0;
  while (!held) {
    const pool = readPool(headers);
    if (pool.expired) { queueExpired.add(1); return; }
    if (pool.error) return;
    if (pool.soldOut) {
      exitSoldOut.add(1);
      http.post(`${BASE}/reservation/leave?scheduleSeq=${SCHEDULE}`, null, { headers, tags: { name: 'leave' } });
      return;
    }
    if (pool.available.length === 0) {
      // 남은 좌석이 모두 누군가 선점 중: 만료로 풀릴 때까지 최대 60초 기다림
      if (Date.now() - t1 > 60 * 1000) {
        exitGaveUp.add(1);
        http.post(`${BASE}/reservation/leave?scheduleSeq=${SCHEDULE}`, null, { headers, tags: { name: 'leave' } });
        return;
      }
      sleep(1);
      continue;
    }
    const n = Math.min(want, pool.available.length);
    const seats = shuffle(pool.available).slice(0, n);
    const res = http.post(`${BASE}/reservation`, JSON.stringify({ scheduleSeq: SCHEDULE, seats }), { headers, tags: { name: 'hold' } });
    if (res.status === 201) {
      held = n;
    } else if (res.status === 409) {
      holdConflicts.add(1);
      sleep(0.2 + Math.random() * 0.3);
    } else if (res.status === 403) {
      queueExpired.add(1);
      return;
    } else {
      return fail('hold', res);
    }
  }
  timeToHold.add(Date.now() - t1);

  // 4. 결제창 (1~3초 고민)
  sleep(1 + Math.random() * 2);
  if (outcome === 'abandon') {
    buyersAbandoned.add(1); // 아무 요청 없이 이탈 → 선점 TTL 만료로 반납
    return;
  }
  // 멱등키: 이 결제 시도에 하나. 같은 키로 여러 번 보내도 서버는 한 번만 결제한다
  const payParams = { headers: { ...headers, 'Idempotency-Key': `${memberId}-${Date.now()}-${Math.random()}` }, tags: { name: 'payment' } };
  const payBody = JSON.stringify({ scheduleSeq: SCHEDULE, payMethod: 'CARD', mockResult: outcome === 'fail' ? 'FAIL' : 'SUCCESS' });
  let pay;
  if (outcome === 'pay' && (vu % 10 === 2 || vu % 10 === 7)) {
    // 더블클릭: 같은 키로 2번 동시에. 하나는 200, 다른 하나는 200(같은 결과) 또는 409(처리 중)
    doubleClicks.add(1);
    const [a, b] = http.batch([
      ['POST', `${BASE}/payment`, payBody, payParams],
      ['POST', `${BASE}/payment`, payBody, payParams],
    ]);
    check([a, b], { 'double click: at least one 200': (rs) => rs.some((r) => r.status === 200) });
    pay = a.status === 200 ? a : b;
  } else {
    pay = http.post(`${BASE}/payment`, payBody, payParams);
  }
  if (outcome === 'fail') {
    check(pay, { 'declined is 402': (r) => r.status === 402 }) ? buyersDeclined.add(1) : fail('payment_fail', pay);
  } else if (check(pay, { 'payment 200': (r) => r.status === 200 })) {
    buyersPaid.add(1);
    seatsSold.add(held);
  } else {
    fail('payment', pay);
  }
}
