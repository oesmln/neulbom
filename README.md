# 늘봄 (NEULBOM)

늘봄은 사용자가 가정에서도 간단하게 인지 건강 상태를 확인하고, 일상 기록을 꾸준히 관리할 수 있도록 돕습니다. 사용자는 AI 캐릭터와 대화하며 검사와 기록을 진행하고, 보호자는 여러 사용자의 검사 결과와 인지 변화 추이를 확인할 수 있습니다.

> 검사 및 AI 분석 결과는 참고 자료이며, 의료적 진단을 대신하지 않습니다.

## 주요 기능

### 사용자 기능

- CIST 기반 최초 인지 선별검사
- AI 캐릭터와의 정서 문답
- 음성 및 텍스트 기반 일상 기록
- 인지기능 진단 게임
- 검사 결과와 인지 변화 확인
- 지역 건강 캠페인 및 일정 확인
- 서비스 관련 알림 확인

### 보호자 기능

- 여러 사용자 프로필 관리
- 사용자의 인지 점수와 주요 활동 지표 확인
- 인지 저하 위험 추이 차트 확인
- 사용자가 작성한 일기 열람 및 반응
- 검사 결과와 활동 기록 확인

## 개발 환경

| 구분 | 기술 및 도구 |
| --- | --- |
| Frontend | React Native |
| Backend | Spring Boot (Java) |
| AI Server | FastAPI, PyTorch, Transformers, scikit-learn |
| Database | PostgreSQL |
| STT | Google Cloud Speech-to-Text V2 (Chirp 3) |
| Text Classification | KcELECTRA |
| Text Generation / Summary | Gemini API |
| Audio Analysis | AST(Audio Spectrogram Transformer) |
| Model Experiment | Google Colab |
| Character Creation | Blender, Meshy AI |

## 디렉터리 구조

```text
.
├── frontend/          # React Native 애플리케이션
├── backend/           # Spring Boot 서버 및 API
├── ai-server/         # 음성·텍스트 기반 AI 분석 서버
├── docs/              # 협업 규칙과 개발 문서
├── .gitignore
└── README.md
```

## 실행 방법

현재 `frontend/`와 `backend/`는 초기 디렉터리 단계입니다. 아래 명령은 각 프로젝트 초기화 후 사용하는 기본 실행 방법입니다.

### Frontend

```bash
cd frontend
npm install
npm start
```

Metro 서버 실행 후 별도 터미널에서 플랫폼을 실행합니다.

```bash
npm run ios
# 또는
npm run android
```

### Backend

```bash
cd backend
./gradlew bootRun
```

백엔드 실행 전 PostgreSQL 데이터베이스와 외부 API 키를 프로젝트 설정에 맞게 등록해야 합니다.

### AI Server

```powershell
cd ai-server
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 협업 가이드

- [브랜치 전략](docs/branch-strategy.md)
- [커밋 컨벤션](docs/commit-convention.md)
- [Issue 작성 가이드](docs/issue-guide.md)
- [Pull Request 가이드](docs/pull-request-guide.md)

기본 개발 브랜치는 `develop`입니다. 작업은 Issue를 생성한 뒤 `develop`에서 작업 브랜치를 만들고, 작업 완료 후 `develop`을 대상으로 Pull Request를 생성합니다.

## 서비스별 문서

- [Frontend README](frontend/README.md)
- [Backend README](backend/README.md)
- [AI Server README](ai-server/README.md)
- [운영 배포 README](deploy/README.md)
