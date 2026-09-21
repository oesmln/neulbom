# 늘봄 AI 서버

늘봄 AI 서버는 CIST 검사에서 수집한 음성과 전사문을 분석해 인지 저하 위험 신호를 계산하는 FastAPI 서비스입니다.

AST 음향 분석, KcELECTRA 텍스트 분석, 보조 실패 사건, 응답 지연을 결합하여 최종 fusion 모델 점수를 반환합니다.

> AI 분석 결과는 참고용 위험 신호이며 의료적 진단이나 공식 CIST 30점 점수를 대신하지 않습니다.

## 주요 기능

- Q11 지연회상 결과에 따른 Q12~Q16 조건부 문항 선택
- signed URL을 통한 문항별 음성 다운로드(WAV, M4A, MP3, WebM/Opus)
- 음성 mono·16kHz 전처리
- Silero VAD 기반 첫 발화 및 응답 지연 계산
- 문항별 정오 판정과 보조 `wrong_event` 계산
- AST seed 42·52·62 대상자 logit 평균 앙상블
- KcELECTRA seed 42·52·62 앙상블
- Logistic Regression fusion 추론
- 비동기 분석 작업 생성·조회·재시도
- 멱등 요청 처리
- SQLite 작업 상태 및 재시도 이력 보존
- 서버 재시작 시 중단 작업 복구
- Docker 실행 지원

## 디렉터리 구조

```text
ai-server/
├─ app/
│  ├─ api/                  # FastAPI 라우트, 스키마, 인증, 오류 처리
│  ├─ audio/                # 음성 다운로드, 전처리, VAD
│  ├─ contracts/            # 기준 계약 로더와 검증기
│  ├─ core/                 # 환경 설정, 로깅, 런타임
│  ├─ inference/            # AST, KcELECTRA, fusion 추론
│  ├─ repositories/         # SQLite 저장소
│  ├─ scoring/              # 정오 판정, wrong-event, 응답 지연
│  ├─ services/             # 분석 파이프라인과 비동기 worker
│  └─ main.py
├─ configs/                 # VAD 및 Fusion 위험 단계 운영 설정
├─ contracts/               # AI·백엔드 기준 계약 파일
├─ scripts/                 # VAD 검증 도구
├─ tests/                   # 자동 테스트
├─ validation/              # VAD 보정 결과
├─ Dockerfile
├─ compose.yaml
├─ pyproject.toml
└─ .env.example
```

## 기준 계약

AI 서버와 백엔드는 다음 파일을 버전별 불변 기준본으로 사용합니다.

- `contracts/cist-v1.json`
- `contracts/wrong-event-v1.json`
- `contracts/ai-server-openapi-v1.yaml`

문항 원문, `question_code`, 조건부 시행 규칙, 재시도 계약은 양쪽 서버에서 임의로 변경하지 않습니다.

현재 운영 기준:

- 문항 집합: `cist-v1`
- 보조 실패 사건 규칙: `wrong-event-v1`
- AST 모델: `final_ast_core4_epoch6_3seed_ensemble`
- Fusion 모델: `final_fusion_lr_21subjects_ast20_mean_logit_3seed_v2`
- 하위 선별 임계값: `0.38592870327757767`
- 상위 확인 임계값: `0.8061380697921943`
- 임계값 버전: `fusion-threshold-v2`

### 최종 위험 단계

Fusion Model은 0에서 1 사이의 연속형 위험 점수인
`model_score`를 출력합니다. AI 서버는 이 점수에 두 개의 운영
threshold를 적용하여 다음 세 단계로 구분합니다.

| 점수 범위 | `risk_level` | 화면 표시 |
|---|---|---|
| `p < 0.38592870327757767` | `stable` | 안정적 |
| `0.38592870327757767 <= p < 0.8061380697921943` | `monitoring_needed` | 꾸준한 관찰 필요 |
| `p >= 0.8061380697921943` | `review_needed` | 확인 필요 |

기존 연동 호환성을 위해 `risk_flag`도 함께 반환합니다.

```text
risk_flag = model_score >= 0.38592870327757767
```

최종 결과 예시는 다음과 같습니다.

```json
{
  "model_score": 0.823,
  "decision_threshold": 0.38592870327757767,
  "review_threshold": 0.8061380697921943,
  "threshold_version": "fusion-threshold-v2",
  "risk_flag": true,
  "risk_level": "review_needed"
}
```

백엔드는 AI 서버가 반환한 `model_score`, 두 threshold,
`threshold_version`, `risk_flag`, `risk_level`을 재계산하지 않고
그대로 저장하고 프론트엔드에 전달합니다.

`0.38592870327757767`과 `0.8061380697921943`은 21명의 내부 OOF 결과를 바탕으로 선정한
졸업과제 프로토타입의 잠정 운영 기준이며 외부 검증된 임상 기준이
아닙니다. 결과 화면에서는 의학적 진단이나 확진 결과가 아닌 참고용
스크리닝 결과임을 안내해야 합니다.

## 모델 아티팩트

모델 파일은 크기가 크므로 Git에 커밋하지 않습니다. 로컬 또는 배포 서버의 외부 경로에 다음 구조로 저장합니다.

```text
artifacts/models/
├─ ast/
│  └─ final_ast_core4_epoch6_3seed_ensemble/
│     ├─ ensemble_config.json
│     ├─ seed_42/
│     │  ├─ model.safetensors
│     │  ├─ config.json
│     │  └─ preprocessor_config.json
│     ├─ seed_52/
│     │  └─ ...
│     └─ seed_62/
│        └─ ...
├─ kcelectra/
│  └─ final_kcelectra_service_352clips_seed_ensemble_v1/
│     ├─ ensemble_config.json
│     ├─ run_status.json
│     ├─ runtime_versions.json
│     ├─ seed_42/
│     │  ├─ model.safetensors
│     │  ├─ config.json
│     │  ├─ tokenizer.json
│     │  ├─ tokenizer_config.json
│     │  └─ training_complete.json
│     ├─ seed_52/
│     │  └─ ...
│     └─ seed_62/
│        └─ ...
└─ fusion/
   └─ final_fusion_lr_21subjects_ast20_mean_logit_3seed_v2/
      ├─ final_fusion_lr_pipeline.joblib
      └─ final_fusion_lr_contract.json
```

`runtime_versions.json`은 참고용 선택 파일이며, 모델별 `config.json`에 기록된 필수 버전 검증이 우선합니다.

서버 시작 시 모델 가중치를 메모리에 올리지는 않지만 다음 항목을 사전 검증합니다.

- 필수 디렉터리와 파일 존재 여부
- 파일이 비어 있지 않은지
- seed 42·52·62 구성
- 모델 및 tokenizer 설정
- 모델별 transformers 버전 및 seed 간 일치
- AST sampling rate
- KcELECTRA max length
- AST epoch, pooling 및 대상자 logit 평균 앙상블 방식
- fusion 모델 버전, 특징 순서와 학습 당시 기본 임계값
- fusion 아티팩트와 운영 정책의 하위·상위 임계값 일치

검증에 실패하면 `/health/live`는 정상 응답하지만 `/health/ready`는 `503 MODEL_ARTIFACTS_UNAVAILABLE`을 반환합니다.

Fusion 모델 아티팩트의 `default_binary_threshold=0.5`는 모델 학습 및
기존 평가 당시의 기준값입니다. 실제 서비스의 위험 단계 판정에는
이 값을 직접 사용하지 않습니다.

운영 threshold는 다음 별도 정책 파일에서 관리합니다.

```text
configs/fusion-threshold-v2.json
```

AST는 각 seed에서 세그먼트, 클립, Core4 문항 범주 순서로 logit을
집계하고 클립 수의 제곱근으로 범주를 가중하여 대상자 logit을
계산합니다. 최종 AST 특징은 seed 42·52·62의 대상자 logit을 산술
평균한 값이며, seed별 확률을 평균한 뒤 logit으로 되돌리는 방식은
사용하지 않습니다.

Fusion 모델이 출력한 연속형 위험 점수에는 운영 정책의
`0.38592870327757767`과 `0.8061380697921943`을 적용합니다.

운영 threshold 정책 파일의 Schema, 버전, 임계값 및 위험 단계는
Fusion 추론 서비스가 최초 로딩될 때 검증합니다.

## 로컬 실행

### 1. 가상환경 생성

PowerShell에서 실행합니다.

```powershell
cd C:\github\neulbom\ai-server

python -m venv .venv
.\.venv\Scripts\Activate.ps1

python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
```

이미 가상환경이 있다면 다음 명령으로 활성화합니다.

```powershell
.\.venv\Scripts\Activate.ps1
```

### 2. 환경변수 설정

`.env.example`을 복사해 로컬 `.env`를 만듭니다.

```powershell
Copy-Item .env.example .env
```

최소한 다음 값을 실제 환경에 맞게 변경합니다.

```dotenv
AI_SERVER_APP_ENV=local
AI_SERVER_SERVICE_TOKEN=충분히-긴-서비스간-인증-토큰
# Docker AI 서버가 호스트 백엔드에 접근하는 로컬 주소
AI_SERVER_AUDIO_DOWNLOAD_ALLOWED_HOSTS=host.docker.internal
AI_SERVER_ARTIFACTS_DIR=C:/외부경로/artifacts/models
AI_SERVER_ANALYSIS_DB_PATH=data/analyses.sqlite3
AI_SERVER_IDEMPOTENCY_DB_PATH=data/idempotency.sqlite3
```

`.env`에는 실제 토큰과 절대경로가 포함될 수 있으므로 Git에 커밋하지 않습니다.

로컬에서 AI 서버를 호스트 프로세스로 직접 실행하면 `AI_SERVER_AUDIO_DOWNLOAD_ALLOWED_HOSTS=localhost`로 바꾸고 백엔드의 `AI_AUDIO_PUBLIC_BASE_URL`도 `http://localhost:8080`으로 맞춥니다. 로컬 프로필에서만 명시적으로 허용된 `localhost`, `host.docker.internal` HTTP 주소를 사용할 수 있으며 운영 프로필은 HTTPS signed URL만 허용합니다.

### 3. 서버 실행

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

로컬 API 문서:

```text
http://localhost:8000/docs
```

`--reload`는 로컬 개발에서만 사용합니다.

## 상태 확인

Liveness는 프로세스가 요청을 처리할 수 있는지 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8000/health/live
```

정상 응답:

```json
{
  "status": "ok"
}
```

Readiness는 계약, 모델 아티팩트, SQLite, 분석 worker가 준비됐는지 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8000/health/ready
```

가능한 준비 실패 사유:

- `CONTRACTS_UNAVAILABLE`
- `MODEL_ARTIFACTS_UNAVAILABLE`
- `ANALYSIS_RUNTIME_UNAVAILABLE`

## Docker 실행

Docker용 `.env`에는 다음 값을 설정합니다.

```dotenv
AI_SERVER_APP_ENV=local
AI_SERVER_SERVICE_TOKEN=충분히-긴-서비스간-인증-토큰
AI_SERVER_AUDIO_DOWNLOAD_ALLOWED_HOSTS=host.docker.internal
AI_SERVER_MODEL_ARTIFACTS_HOST_PATH=C:/외부경로/artifacts/models
AI_SERVER_PORT=8000
```

Compose는 `.env`의 `AI_SERVER_APP_ENV`를 컨테이너에 전달합니다. 로컬 실행은 `local`, 운영 배포는 반드시 `production`으로 설정합니다.

설정 검증:

```powershell
docker compose config
```

이미지 빌드:

```powershell
docker compose build
```

컨테이너 실행:

```powershell
docker compose up -d
```

로그 확인:

```powershell
docker compose logs -f ai-server
```

컨테이너 종료:

```powershell
docker compose down
```

`docker compose down`은 SQLite named volume을 유지합니다. `docker compose down -v`는 저장된 분석 작업과 멱등 기록을 삭제하므로 주의해야 합니다.

모델 디렉터리는 컨테이너의 `/app/artifacts/models`에 읽기 전용으로 연결됩니다.

## 실제 모델 스모크 테스트

가짜 사용자 데이터로 실제 AST, KcELECTRA, fusion 모델의 로딩과 최소 추론을 확인합니다.

CPU 실행:

```powershell
.\.venv\Scripts\python.exe -m app.inference.smoke --device cpu
```

자동 장치 선택:

```powershell
.\.venv\Scripts\python.exe -m app.inference.smoke --device auto
```

CUDA 실행:

```powershell
.\.venv\Scripts\python.exe -m app.inference.smoke --device cuda
```

성공 시 `status=passed`와 각 모델의 `seed_count`, 버전, 처리 시간이 출력됩니다. 출력 점수는 합성 입력으로 계산되므로 의학적 의미가 없습니다.

## API 호출 흐름

### 1. Q11 recognition plan 생성

Q11 지연회상 응답을 분석해 시행할 Q12~Q16 문항을 결정합니다.

```text
POST /v1/assessments/{assessment_id}/recognition-plan
```

AI 서버가 반환한 `recalled_units`와 `next_question_codes`를 백엔드가 세션의 `recognition_plan`으로 저장합니다.

### 2. 최종 세션 분석 생성

검사 종료 후 17개 문항 상태를 포함한 분석을 생성합니다.

```text
POST /v1/analyses
```

Q12~Q16 중 선택되지 않은 문항도 누락하지 않고 다음 상태로 포함해야 합니다.

```json
{
  "administration_status": "not_applicable"
}
```

분석 요청이 수락되면 HTTP `202`와 `pending` 상태가 반환됩니다.

### 3. 분석 상태 조회

```text
GET /v1/analyses/{analysis_id}
```

분석 상태:

- `pending`: 처리 대기
- `processing`: 처리 중
- `needs_retry`: 백엔드 또는 사용자 조치 필요
- `completed`: 분석 완료
- `failed`: 재시도할 수 없는 실패

### 4. 분석 재시도

```text
POST /v1/analyses/{analysis_id}/retry
```

재시도에서도 기존 `analysis_id`를 유지합니다.

지원하는 작업:

- `REISSUE_AUDIO_URL`: 동일 녹음의 signed URL만 재발급
- `REPLACE_RESPONSE`: 새 녹음과 전사문으로 응답 교체

한 분석에 URL 재발급과 응답 교체가 함께 필요한 혼합 재시도 항목도 지원합니다.

## 인증

상태 확인 API를 제외한 서비스 API에는 Bearer Token이 필요합니다.

```http
Authorization: Bearer {AI_SERVER_SERVICE_TOKEN}
```

서비스 토큰은 프론트엔드에 전달하지 않고 Spring Boot와 AI 서버 사이에서만 사용합니다.

## 멱등성

다음 요청에는 `Idempotency-Key` 헤더가 필요합니다.

- recognition plan 생성
- 분석 생성
- 분석 재시도

규칙:

- 동일한 네트워크 요청 재전송에는 같은 키 사용
- 분석 생성과 재시도는 서로 다른 키 사용
- 첫 번째 재시도와 두 번째 재시도도 서로 다른 키 사용
- 같은 키로 다른 본문을 보내면 `IDEMPOTENCY_CONFLICT`
- `analysis_id`는 재시도에서도 유지

예:

```http
Idempotency-Key: analysis-create-{고유값}
```

## signed URL 재시도

음성은 AI 서버에 직접 업로드하지 않고 signed URL로 전달합니다. 로컬 프로필에서는 허용된 호스트의 HTTP 주소도 사용할 수 있고, 운영 프로필에서는 HTTPS signed URL만 허용합니다.

권장 유효시간은 30분입니다.

URL 만료 또는 접근 실패 시 AI 서버는 다음 상태를 반환할 수 있습니다.

```json
{
  "status": "needs_retry",
  "reason_code": "AUDIO_URL_EXPIRED",
  "retry_items": [
    {
      "required_action": "REISSUE_AUDIO_URL"
    }
  ]
}
```

백엔드는 같은 `recording_id`, `response_id`를 유지한 채 URL만 재발급합니다.

## 무응답과 채점 제외

다음 두 상태는 서로 다른 의미입니다.

- `vad_status=no_response`: 사용할 수 있는 발화가 검출되지 않음
- `scoring_status=not_scored`: 공식 정오 채점 대상이 아님

장소 문항은 질문과 분석 입력에는 포함하지만 자동 정오 판정과 공식 점수에서는 제외합니다.

보조 `wrong_event`는 공식 CIST 점수가 아니며 최종 fusion 입력으로만 사용합니다.

## 재시도 사유 코드

- `INCOMPLETE_ASSESSMENT`
- `UNSCORABLE_STT`
- `AUDIO_URL_EXPIRED`
- `AUDIO_DOWNLOAD_FAILED`
- `UNSUPPORTED_AUDIO_FORMAT`

복구할 수 없는 모델 또는 내부 오류는 각각 `MODEL_UNAVAILABLE`, `INTERNAL_ERROR`로 `failed` 처리됩니다.

## 데이터 저장과 복구

로컬 실행에서는 다음 SQLite 파일을 사용합니다.

```text
data/
├─ analyses.sqlite3
└─ idempotency.sqlite3
```

Docker에서는 `/app/data`가 `ai-server-data` named volume에 저장됩니다.

서버 시작 시 이전 실행에서 남은 `pending`, `processing` 작업을 복구해 다시 대기열에 등록합니다. 모델 추론은 동시에 여러 건 실행하지 않고 단일 worker가 순차 처리합니다.

SQLite 파일을 백업할 때는 AI 서버를 종료한 뒤 파일 또는 Docker volume을 복사하는 방식을 권장합니다.

## 테스트

전체 자동 테스트:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

특정 테스트:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_analysis_api.py -q
```

테스트는 임시 SQLite DB와 가짜 모델을 사용합니다. 실제 모델 검증은 별도의 스모크 명령으로 실행합니다.

## 운영 주의사항

- `.env`, 서비스 토큰, 음성 파일, 전사문을 Git에 커밋하지 않습니다.
- `model.safetensors`, `joblib` 모델 파일을 Git에 커밋하지 않습니다.
- signed URL과 전사문을 로그에 기록하지 않습니다.
- 운영에서는 Uvicorn worker를 1개로 유지합니다.
- 현재 분석 처리 제한 시간은 300초입니다.
- 모델 또는 임계값 변경 시 새로운 버전으로 계약과 결과를 보존합니다.
