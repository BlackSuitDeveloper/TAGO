# TAGO AWS EC2 + Docker 3-스택 배포 가이드

## 1. 아키텍처 구성 (3갈래 Compose 스택)

| 스택 | Compose 파일 | 포함 컨테이너 | 외부 공개 포트 |
| :--- | :--- | :--- | :--- |
| **1. App** | `compose.app.yml` | `frontend`(Nginx + React), `backend`(Spring Boot) | `:80` (Nginx) |
| **2. Data** | `compose.data.yml` | `postgres`, `redis` | 없음 (내부망 `tago-net` 전용) |
| **3. Monitoring** | `compose.monitoring.yml` | `prometheus`, `grafana`, `loki`, `promtail`, `alertmanager`, `postgres-exporter`, `redis-exporter` | `:3000` (Grafana, 내 IP만 허용) |

---

## 2. 로컬 PC: 앱 이미지 빌드 & Docker Hub 업로드

```bash
# 1. Docker Hub 로그인
docker login

# 2. 백엔드·프론트엔드 이미지 빌드
docker build -t <dockerhub-id>/tago-backend:latest .
docker build -t <dockerhub-id>/tago-frontend:latest --build-arg VITE_API_BASE_URL=/api/v1 ./frontend/TAGO_Front

# 3. Docker Hub 푸시
docker push <dockerhub-id>/tago-backend:latest
docker push <dockerhub-id>/tago-frontend:latest
```

---

## 3. AWS EC2 초기 세팅 (최초 1회)

### 보안그룹 인바운드 규칙
- `80` (HTTP): `0.0.0.0/0` (전체 사용자 접속)
- `22` (SSH): **내 IP만 허용**
- `3000` (Grafana): **내 IP만 허용**
- *(5432, 6379, 8080, 9090 등 내부 포트는 절대 열지 않음)*

### EC2 접속 및 Docker·Swap 설치
```bash
# 로컬에서 접속
chmod 400 <key>.pem
ssh -i <key>.pem ubuntu@<EC2-PUBLIC-IP>

# Docker 설치 (Ubuntu 24.04)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker

# Swap 메모리 2GB 설정 (저사양 EC2 OOM 방지)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 4. EC2 배포 실행

```bash
# 1. 프로젝트 설정 파일 가져오기
git clone https://github.com/BlackSuitDeveloper/TAGO.git
cd TAGO

# 2. 운영 환경변수 작성 (.env)
cp .env.prod.example .env
nano .env
chmod 600 .env

# 3. 전체 3-스택 기동
chmod +x scripts/deploy.sh
./scripts/deploy.sh all
```

### 추후 앱 코드만 업데이트 재배포할 때 (DB/Redis/모니터링 무중단)
```bash
./scripts/deploy.sh app
```

---

## 5. 배포 후 점검 명령어

```bash
# 컨테이너 상태 확인
docker ps

# 백엔드 헬스체크
docker exec tago-backend curl -i http://localhost:8080/actuator/health

# Redis 연결 확인
docker exec tago-redis redis-cli ping

# Nginx -> Spring 프록시 및 화면 확인
curl -I http://localhost

# 백엔드 실시간 로그 확인 (종료: Ctrl+C)
docker logs -f --tail 100 tago-backend
```
