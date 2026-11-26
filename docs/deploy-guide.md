# TAGO AWS EC2 (3-서버) 인프라 배포 가이드

## 1. 서버별 구성 (`infra/`)

| 서버 | 서브넷 / 보안그룹 | 구성 파일 | 컨테이너 (메모리 한도) |
| :--- | :--- | :--- | :--- |
| **EC2-web** | Public (`sg-web`, Elastic IP) | `infra/web/docker-compose.yml` | `nginx` (`1.30.8-alpine`, 256m), `app` (`21.0.13_9-jre-alpine`, 1g, `:8080` 내부 / `:8081` actuator), *(monitoring)* `node-exporter` (`v1.12.1`, 64m), `promtail` (`3.6.11`, 64m) |
| **EC2-data** | Private (`sg-data`, 공인 IP 없음) | `infra/data/docker-compose.yml` | `postgres` (`18.7-alpine`, 1g), `redis` (`8.4.8-alpine`, 384m, 비밀번호·AOF·`noeviction`), *(monitoring)* `postgres-exporter` (`v0.21.1`, 64m), `redis-exporter` (`v1.99.0`, 64m), `node-exporter` (`v1.12.1`, 64m) |
| **EC2-ops** | Private (`sg-ops`, 공인 IP 없음) | `infra/ops/docker-compose.yml` | `prometheus` (`v3.5.5`, 768m, 7일 보존), `grafana` (`12.4.16`, 512m), `loki` (`3.6.22`, 256m), `alertmanager` (`v0.34.2`, 128m) |

- 모든 컨테이너 로그는 `json-file` (`max-size: 10m`, `max-file: 3`)로 제한됩니다.
- `prometheus(:9090)`, `grafana(:3000)`, `alertmanager(:9093)`는 `127.0.0.1`에만 바인딩되어 SSH 터널로만 접근합니다.

---

## 2. 보안그룹 규칙

| 보안그룹 | 포트 | 허용 출처 | 용도 |
| :--- | :--- | :--- | :--- |
| `sg-web` | `80, 443` | `0.0.0.0/0` | 사용자 웹 접속 |
| `sg-web` | `22` | 내 IP | SSH 관리 접속 (점프 호스트) |
| `sg-web` | `8081, 9100` | `sg-ops` | Prometheus 수집 (Actuator, Node Exporter) |
| `sg-data` | `5432, 6379` | `sg-web` | 앱 서버의 PostgreSQL·Redis 접속 |
| `sg-data` | `9100, 9187, 9121` | `sg-ops` | Prometheus 수집 |
| `sg-data` | `22` | `sg-web` | web 경유 SSH |
| `sg-ops` | `3100` | `sg-web` | Promtail → Loki 로그 전송 |
| `sg-ops` | `22` | `sg-web` | web 경유 SSH (Grafana/Prometheus 터널) |

---

## 3. 실행 순서 (`data` → `web` → `ops`)

```bash
# 1) EC2-data
cd infra/data && cp .env.example .env && chmod 600 .env && nano .env
docker compose up -d
docker compose ps   # postgres, redis healthy 확인

# 2) EC2-web (.env 의 DATA_HOST = EC2-data 프라이빗 IP)
cd infra/web && cp .env.example .env && chmod 600 .env && nano .env
docker compose up -d --build
docker compose logs -f app

# 3) EC2-ops (prometheus/prometheus.yml 의 WEB_IP, DATA_IP 교체 후)
cd infra/ops && cp .env.example .env && chmod 600 .env && nano .env
docker compose up -d

# 4) 모니터링 수집기 활성화 (EC2-web, EC2-data 각각 실행)
docker compose --profile monitoring up -d

# 5) 노트북에서 Grafana / Prometheus 접속 (SSH 터널)
ssh -J ubuntu@<EC2-web 공인IP> -L 3000:localhost:3000 -L 9090:localhost:9090 ubuntu@<EC2-ops 프라이빗IP>
# 브라우저: http://localhost:3000 (Grafana), http://localhost:9090 (Prometheus)
```
