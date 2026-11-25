import client from './client';

const POLL_INTERVAL_MS = 700;

// 대기열 진입. 입장은 서버 스케줄러가 1초마다 처리하므로,
// 붐비지 않을 때는 잠깐(waitMs) 기다리면 바로 입장된다. 그래도 입장이 안 되면 대기 상태를 그대로 돌려준다.
export async function enterQueue(scheduleSeq, waitMs = 3000) {
  const res = await client.post('/queue', { scheduleSeq });
  let data = res.data || {};
  const deadline = Date.now() + waitMs;
  while (!data.isAvailable && data.queueToken && Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    const poll = await client.get(`/queue/${data.queueToken}`);
    data = { ...data, ...(poll.data || {}) };
  }
  return data;
}
