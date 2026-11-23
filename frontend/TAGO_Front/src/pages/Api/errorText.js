/**
 * 서버 오류 응답에서 사용자에게 보여줄 문구를 고른다.
 * 서버가 준 message(ResWrapper 오류 형식) > 문자열 본문 > 호출하는 쪽의 기본 문구 순서.
 * 서버에 닿지 못한 경우(응답 없음)는 연결 안내 문구를 쓴다.
 */
export default function errorText(err, fallback) {
  if (!err?.response) return '서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.';
  const data = err.response.data;
  if (typeof data?.message === 'string' && data.message) return data.message;
  if (typeof data === 'string' && data) return data;
  return fallback;
}
