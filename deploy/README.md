# 운영 서버 배포

GCP Compute Engine VM 한 대에서 Expo Web 정적 빌드를 포함한 Nginx, Spring Boot,
FastAPI AI 서버, PostgreSQL을 Docker Compose로 실행한다. Redis는 현재
애플리케이션에서 사용하지 않으므로 포함하지 않는다. 외부에는 Nginx의 80/443
포트만 공개된다.

## 1. VM 준비

- Ubuntu 24.04, 서울 리전, 4 vCPU/16GB부터 시작한다.
- Docker Engine과 Compose plugin, Git을 설치한다.
- VM에는 Speech-to-Text와 Text-to-Speech 사용 권한을 가진 서비스 계정을 연결한다.
- 방화벽에서는 SSH 관리 경로와 HTTP/HTTPS만 허용한다. 8080, 8000, 5432는 열지 않는다.
- 저장 경로를 만든다.

```bash
sudo mkdir -p /opt/neulbom/models /opt/neulbom/certs /opt/neulbom/app
sudo chown -R "$USER":"$USER" /opt/neulbom
```

AI 모델 파일은 `/opt/neulbom/models`에 직접 업로드한다. 저장소에는 커밋하지 않는다.

## 2. DuckDNS와 TLS

도메인은 DuckDNS 고정 주소를 사용한다. 먼저 GCP에서 고정 외부 IP를 예약하고,
DuckDNS 콘솔에서 해당 IP를 A 레코드로 연결한다. VM을 중지했다가 다시 시작해도
고정 IP와 주소가 유지되도록 반드시 고정 IP를 사용한다.

Ubuntu VM에 Certbot을 설치한다.

```bash
sudo apt-get update
sudo apt-get install -y certbot
sudo mkdir -p /opt/neulbom/acme /opt/neulbom/certs
```

`deploy/.env`에 실제 DuckDNS 주소를 입력하고 처음에는 HTTP 구성으로 Nginx를
실행한다.

```ini
PUBLIC_API_HOST=neulbom.duckdns.org
NGINX_CONFIG_FILE=http.conf
TLS_CERTS_HOST_PATH=/opt/neulbom/certs
ACME_WEBROOT_HOST_PATH=/opt/neulbom/acme
```

```bash
./deploy/scripts/deploy.sh
sudo certbot certonly --webroot \
  -w /opt/neulbom/acme \
  -d neulbom.duckdns.org \
  --email 관리자이메일 \
  --agree-tos \
  --no-eff-email
```

인증서를 Nginx가 읽을 수 있는 운영 경로로 복사한다.

```bash
sudo cp -L /etc/letsencrypt/live/neulbom.duckdns.org/fullchain.pem /opt/neulbom/certs/fullchain.pem
sudo cp -L /etc/letsencrypt/live/neulbom.duckdns.org/privkey.pem /opt/neulbom/certs/privkey.pem
sudo chmod 644 /opt/neulbom/certs/fullchain.pem
sudo chmod 600 /opt/neulbom/certs/privkey.pem
```

그 다음 HTTPS 구성으로 바꾸고 Nginx를 재시작한다.

```ini
NGINX_CONFIG_FILE=https.conf
```

```bash
docker compose --env-file deploy/.env -f deploy/compose.prod.yml up -d nginx
```

인증서 만료 전 자동 갱신은 다음 스크립트를 주기적으로 실행한다.

```bash
./deploy/scripts/renew-certificate.sh neulbom.duckdns.org
```

Nginx가 사용하는 인증서는 아래 이름으로 유지한다.

```text
/opt/neulbom/certs/fullchain.pem
/opt/neulbom/certs/privkey.pem
```

인증서 준비 전 origin 연결만 점검할 때는 `NGINX_CONFIG_FILE=http.conf`를 사용할
수 있다. 이 구성은 평문 HTTP이므로 실제 앱 연결에는 사용하지 않는다.

## 3. 환경변수

```bash
cd /opt/neulbom/app
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
```

`deploy/.env`에서 최소한 아래 항목을 설정한다.

- `PUBLIC_API_HOST`: `https://`와 경로를 제외한 API 호스트
- `POSTGRES_PASSWORD`
- `JWT_SECRET`, `AI_SERVER_SERVICE_TOKEN`, `AI_AUDIO_SIGNING_SECRET`: 각각 독립적인 32자 이상 난수
- `CORS_ALLOWED_ORIGINS`: 공개 HTTPS origin
- `GOOGLE_STT_PROJECT_ID`, 필요하면 `GOOGLE_TTS_PROJECT_ID`
- OAuth, Gemini, SMTP를 발표에서 사용할 경우 각 provider 값

난수는 VM에서 다음처럼 생성할 수 있다.

```bash
openssl rand -hex 32
```

실제 값은 Git, 메신저, `.env.example`에 기록하지 않는다.

## 4. 배포 및 확인

```bash
./deploy/scripts/deploy.sh
docker compose --env-file deploy/.env -f deploy/compose.prod.yml logs --tail=200
curl -fsS "https://${PUBLIC_API_HOST}/health"
curl -fsS "https://${PUBLIC_API_HOST}/actuator/health/readiness"
```

AI readiness는 외부에 공개하지 않는다. VM 내부에서 확인한다.

```bash
docker compose --env-file deploy/.env -f deploy/compose.prod.yml exec ai-server \
  python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health/ready').read().decode())"
```

Compose의 `backend-uploads`, `ai-server-data`, `postgres-data` 볼륨은 컨테이너를
재생성해도 유지된다. `docker compose down -v`는 데이터를 삭제하므로 실행하지 않는다.

## 5. 자동 배포

`.github/workflows/deploy-production.yml`은 `develop`의 서버 관련 파일이 바뀌면
VM에 접속해 fast-forward 갱신 후 Compose를 다시 빌드한다. GitHub Environment
`production`에 다음 값을 등록한다.

- Variables: `GCP_PROJECT_ID`, `GCP_ZONE`, `GCP_VM_NAME`
- Secrets: `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOY_SERVICE_ACCOUNT`

VM의 `/opt/neulbom/app`에는 저장소가 clone되어 있어야 하고, private 저장소라면 읽기
전용 GitHub deploy key를 등록한다. GitHub Actions용 GCP 서비스 계정에는 대상 VM에
접속하는 최소 권한만 부여한다. 앱이 Google API에 사용하는 VM 서비스 계정과 배포용
서비스 계정은 분리한다.

배포 실패 시 기존 컨테이너는 가능한 한 유지된다. 이전 검증 커밋으로 되돌릴 때는 VM에서
다음 명령을 실행한다.

```bash
./deploy/scripts/rollback.sh <검증된-커밋-또는-태그>
```

## 6. 프론트엔드 연결

동일한 공개 주소의 `/`에서는 Expo Web 앱이 열리고 `/api/v1`은 백엔드로 전달된다.
웹 빌드에도 호스트만 주입되며 `/api/v1`은 앱 코드가 붙인다. 카카오·네이버 웹
callback은 각각 아래 주소로 빌드된다.

```text
https://api.neulbom.example/auth/callback/kakao
https://api.neulbom.example/auth/callback/naver
```

실제 주소를 provider 콘솔과 백엔드 allowlist에도 똑같이 등록해야 한다.
EAS `preview` environment에도 같은 호스트만 설정한다.

```ini
EXPO_PUBLIC_API_BASE_URL=https://api.neulbom.example
```

프론트 코드가 `develop`에 반영되면 자동 배포가 Expo Web 정적 파일을 다시 빌드한다.
APK는 자동 갱신되지 않으므로 환경변수나 프론트 코드가 바뀌면 Preview APK를 다시
빌드해 설치한다. 진행 중인 요청은 서버 재시작 시 실패할 수 있으므로 발표 중에는
배포하지 않는다.
