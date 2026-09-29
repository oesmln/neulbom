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

GCE 생성 시 별도 영구 디스크를 `neulbom-data` 장치 이름으로 연결하고
`deploy/scripts/bootstrap-vm.sh`를 startup script로 지정하면 `/opt/neulbom` 마운트와
Docker Engine, Compose, Certbot 설치가 자동으로 수행된다. Docker의 data root도 이
영구 디스크에 두므로 named volume과 이미지가 VM 부팅 디스크와 분리된다.

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

VM에서는 저장소에 포함된 systemd unit을 설치해 매일 갱신 여부를 확인한다.

```bash
sudo cp deploy/systemd/neulbom-cert-renew@.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now neulbom-cert-renew@neulbom.duckdns.org.timer
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
- `GEMINI_API_KEY`: 일상 문답 질문과 일기용 세션 요약에 필요
- `GEMINI_MODEL`: 기본 `gemini-3.5-flash-lite`; VM의 기존 `.env`에 이전 모델이 적혀 있으면 교체
- OAuth와 SMTP를 사용할 경우 각 provider 값

난수는 VM에서 다음처럼 생성할 수 있다.

```bash
openssl rand -hex 32
```

실제 값은 Git, 메신저, `.env.example`에 기록하지 않는다.

일상 문답 질문과 새 세션 요약은 같은 `GEMINI_MODEL`을 사용한다. 키가 없으면
Compose 설정 단계에서 배포가 중단된다. 기존 세션 요약이 없는 일기는 요약 호출에
실패하면 생성되지 않으므로, 모델을 바꾼 뒤 질문 생성과 요약 생성까지 확인한다.

## 4. 배포 및 확인

운영 VM에서는 `deploy/.env`를 `/opt/neulbom/app/deploy/.env`에 두고 권한을
`600`으로 유지한다. 자동 배포는 추적 파일만 `/opt/neulbom/releases/<커밋 SHA>`에
풀어 놓으므로, `.env`와 업로드·모델·인증서 데이터는 덮어쓰지 않는다.

마지막 자동 배포 소스를 다시 빌드할 때는 다음 명령을 사용한다.

```bash
revision="$(sudo cat /opt/neulbom/app/.deploy-revision)"
release="/opt/neulbom/releases/${revision}"
sudo env DEPLOY_ENV_FILE=/opt/neulbom/app/deploy/.env IMAGE_TAG="${revision}" \
  "${release}/deploy/scripts/deploy.sh"
```

```bash
sudo docker compose --env-file /opt/neulbom/app/deploy/.env \
  -f "/opt/neulbom/releases/$(sudo cat /opt/neulbom/app/.deploy-revision)/deploy/compose.prod.yml" \
  logs --tail=200
curl -fsS "https://${PUBLIC_API_HOST}/health"
curl -fsS "https://${PUBLIC_API_HOST}/actuator/health/readiness"
```

Gemini 설정을 바꾼 뒤에는 백엔드 컨테이너에 적용된 모델명과 키 존재 여부를
값을 노출하지 않고 확인한다.

```bash
revision="$(sudo cat /opt/neulbom/app/.deploy-revision)"
sudo docker compose --env-file /opt/neulbom/app/deploy/.env \
  -f "/opt/neulbom/releases/${revision}/deploy/compose.prod.yml" exec -T backend \
  sh -c 'test -n "$GEMINI_API_KEY" && printf "GEMINI_MODEL=%s\n" "$GEMINI_MODEL"'
```

`/actuator/health/readiness`는 외부 Gemini 호출까지 확인하지 않는다. 배포 후에는
테스트 계정으로 일상 문답의 첫 질문을 받고, 답변을 저장한 뒤 후속 질문과
세션 요약·일기 생성 결과를 확인한다. 외부 provider의 일시적 `429`·`5xx`는
재시도될 수 있으므로 지속되는 실패와 구분한다.

AI readiness는 외부에 공개하지 않는다. VM 내부에서 확인한다.

```bash
revision="$(sudo cat /opt/neulbom/app/.deploy-revision)"
sudo docker compose --env-file /opt/neulbom/app/deploy/.env \
  -f "/opt/neulbom/releases/${revision}/deploy/compose.prod.yml" exec -T ai-server \
  python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health/ready').read().decode())"
```

Compose의 `backend-uploads`, `ai-server-data`, `postgres-data` 볼륨은 컨테이너를
재생성해도 유지된다. `docker compose down -v`는 데이터를 삭제하므로 실행하지 않는다.

## 5. 자동 배포

`.github/workflows/deploy-production.yml`은 `develop`의 서버 관련 파일이 바뀌면
해당 커밋의 추적 파일만 묶어 VM으로 전달하고 Compose를 다시 빌드한다. VM에 Git
clone이나 장기 SSH 키를 둘 필요가 없다. GitHub Environment `production`에는 다음
값을 등록한다.

- Variables: `GCP_PROJECT_ID`, `GCP_ZONE`, `GCP_VM_NAME`
- Secrets: `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOY_SERVICE_ACCOUNT`

현재 `production` Environment 값은 다음과 같다.

| Name | Type | Value |
| --- | --- | --- |
| `GCP_PROJECT_ID` | Variable | `neulbom-505515` |
| `GCP_ZONE` | Variable | `asia-northeast3-a` |
| `GCP_VM_NAME` | Variable | `neulbom-prod` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Secret | `projects/31496429269/locations/global/workloadIdentityPools/github-actions/providers/neulbom-production` |
| `GCP_DEPLOY_SERVICE_ACCOUNT` | Secret | `neulbom-github-deploy@neulbom-505515.iam.gserviceaccount.com` |

Environment 배포 브랜치는 `develop`만 허용한다.

GitHub Actions는 서비스 계정 키 JSON 대신 Workload Identity Federation을 사용한다.
Provider 조건은 저장소 `oesmln/neulbom`, 브랜치 `develop`, Environment `production`으로
제한한다. 배포 서비스 계정에는 대상 VM의 instance metadata 수정 권한, IAP의 VM별
TCP 22 tunnel 권한, IAP target 조회에 필요한 `compute.instances.list` 읽기 권한만
부여하고, VM의 runtime 서비스 계정에 `roles/iam.serviceAccountUser`를 부여한다.
쓰기 권한은 배포 VM에만 적용한다. 앱이 Google API에 사용하는 VM 서비스 계정과 배포용
서비스 계정은 분리한다.

VM에는 IAP SSH용 TCP 22 인바운드 규칙을 `35.235.240.0/20`에서 `neulbom-server`
네트워크 태그 대상으로만 허용한다. 워크플로는 매 실행마다 60분 후 만료되는 SSH 키를
만들고 종료 시 VM metadata에서 제거한다. 배포 서비스 계정은 `neulbom-ai-runtime`과
분리한다.

VM의 `gha-deploy` 계정은 Docker 배포와 상태 확인을 위해 비밀번호 없는 sudo 권한이
필요하다. GCE의 `google-sudoers` 그룹에 한 번 등록하고 확인한다.

```bash
sudo id -u gha-deploy >/dev/null 2>&1 || sudo useradd --create-home --shell /bin/bash gha-deploy
sudo usermod -aG google-sudoers gha-deploy
sudo -u gha-deploy sudo -n true
```

배포 소스는 `/opt/neulbom/releases/<커밋 SHA>`에 보관한다. 컨테이너 재생성과 네 가지
서비스의 health/readiness, backend의 `ffmpeg` 설치를 확인한 뒤
`/opt/neulbom/app/.deploy-revision`을 갱신한다. 실패하면 해당 파일은 이전 성공 revision을
유지한다.

이전 배포로 되돌릴 때는 VM에 보관된 release와 이미지 tag를 사용한다.

```bash
revision="$(sudo cat /opt/neulbom/app/.deploy-revision)"
sudo "/opt/neulbom/releases/${revision}/deploy/scripts/rollback.sh" \
  <검증된-커밋-SHA>
```

GitHub Actions의 `workflow_dispatch`는 `develop`에서만 실행된다. production 환경 설정이
누락되었거나 권한 전파 중이면 Actions 로그에 나온 GCP 인증·IAP 오류를 확인한다.

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
