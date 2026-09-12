# 발표용 서버 배포

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

## 2. 도메인과 TLS

Cloudflare DNS/Proxy 또는 DuckDNS와 Let's Encrypt로 공개 도메인을 VM에 연결한다.
Nginx가 사용하는 인증서는 아래 이름으로 준비한다.

```text
/opt/neulbom/certs/fullchain.pem
/opt/neulbom/certs/privkey.pem
```

Cloudflare를 사용하면 SSL/TLS 모드는 `Full (strict)`로 설정하고 Cloudflare Origin
Certificate 또는 공개 CA 인증서를 사용한다. Flexible 모드는 사용하지 않는다.

인증서 준비 전 origin 연결만 점검할 때는 `.env`의
`NGINX_CONFIG_FILE=http.conf`를 사용할 수 있다. 이 구성은 평문 HTTP이므로 APK나
실제 발표 연결에는 사용하지 않는다.

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

`.github/workflows/deploy-presentation.yml`은 `develop`의 서버 관련 파일이 바뀌면
VM에 접속해 fast-forward 갱신 후 Compose를 다시 빌드한다. GitHub Environment
`presentation`에 다음 값을 등록한다.

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
