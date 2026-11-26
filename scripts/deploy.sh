#!/bin/bash
set -euo pipefail

# ============================================================================
# TAGO 3-스택 배포 스크립트 (App / Data / Monitoring)
# 사용법:
#   ./scripts/deploy.sh all         # 전체 기동 (data -> app -> monitoring)
#   ./scripts/deploy.sh app         # 앱(Nginx + Spring)만 무중단 pull & 재기동
#   ./scripts/deploy.sh data        # DB + Redis 기동
#   ./scripts/deploy.sh monitoring  # 모니터링 스택 기동
# ============================================================================

TARGET="${1:-app}"

if [ ! -f ".env" ]; then
  echo "[ERROR] .env 파일이 없습니다. .env.prod.example을 복사해 .env를 먼저 작성하세요."
  exit 1
fi

# 공용 내부 네트워크 생성 (이미 있으면 통과)
if ! docker network inspect tago-net >/dev/null 2>&1; then
  echo "[INFO] Docker 공용 네트워크(tago-net) 생성 중..."
  docker network create tago-net
fi

deploy_data() {
  echo "[INFO] 2번 스택(Data: Postgres + Redis) 기동..."
  docker compose -f compose.data.yml up -d
}

deploy_app() {
  echo "[INFO] 1번 스택(App: Nginx + Spring) 최신 이미지 pull 및 기동..."
  docker compose -f compose.app.yml pull || true
  docker compose -f compose.app.yml up -d
}

deploy_monitoring() {
  echo "[INFO] 3번 스택(Monitoring: Prometheus + Grafana + Loki + Alertmanager) 기동..."
  docker compose -f compose.monitoring.yml up -d
}

case "$TARGET" in
  all)
    deploy_data
    deploy_app
    deploy_monitoring
    ;;
  app)
    deploy_app
    ;;
  data)
    deploy_data
    ;;
  monitoring)
    deploy_monitoring
    ;;
  *)
    echo "사용법: $0 {all|app|data|monitoring}"
    exit 1
    ;;
esac

echo "[DONE] 현재 실행 중인 컨테이너:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
