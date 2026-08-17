# AGENTS.md

이 문서는 늘봄(NEULBOM) 저장소 전체에 적용되는 공통 규칙이다. 저장소 루트는
`/Users/kimminseo/neulbom`이며, 이 문서를 기준으로 이슈를 분석하고 구현 범위를
판단한다.

## 1. 적용 범위

- 이슈 파서와 AI 에이전트의 기본 탐색 범위는 `backend/`가 아니라 저장소 전체다.
- 이슈를 분석할 때 `frontend/`, `backend/`, `docs/`, 루트 설정 파일을 모두 확인한다.
- 이슈 제목의 scope가 특정 영역을 가리키더라도 관련 공통 문서와 다른 앱 영역의
  연동 영향을 함께 확인한다.
- `backend/AGENTS.md`는 이 문서의 규칙을 상속하는 백엔드 전용 추가 규칙이다.
- 하위 디렉터리에 별도의 `AGENTS.md`가 있으면 저장소 전체 규칙과 함께 적용한다.

## 2. 이슈 파서 기준 경로

이슈 파서, 변경 영향 분석, 파일 검색은 항상 저장소 루트에서 시작한다. `backend/`
를 검색 루트로 하드코딩하지 않는다.

```text
repository root: .
included: frontend/, backend/, docs/, root files
```

Git에 등록된 전체 파일을 기준으로 확인할 때는 다음 명령을 사용한다.

```bash
git ls-files
```

로컬 파일까지 재귀적으로 확인해야 할 때는 저장소 루트에서 실행하고, 생성물과
의존성 디렉터리만 제외한다.

```bash
find . \
  -path './.git' -prune -o \
  -path './backend/build' -prune -o \
  -path './backend/.gradle' -prune -o \
  -path './frontend/node_modules' -prune -o \
  -path './frontend/dist' -prune -o \
  -type f -print
```

다음 파일과 디렉터리는 이슈 분석 대상에서 제외한다.

- `.git/`
- `backend/build/`
- `backend/.gradle/`
- `backend/uploads/`
- `frontend/node_modules/`
- `frontend/dist/`
- `.env` 및 실제 비밀정보가 포함된 로컬 설정 파일

## 3. 프로젝트 구조

```text
.
├── frontend/       React Native 애플리케이션
├── backend/        Spring Boot 서버 및 API
├── docs/           협업 규칙과 통합 문서
└── README.md
```

주요 기준 문서는 다음과 같다.

- API 정의: [`backend/docs/api-spec.md`](backend/docs/api-spec.md)
- 백엔드 개발 체크리스트: [`backend/docs/backend-development-checklist.md`](backend/docs/backend-development-checklist.md)
- 프론트엔드·백엔드 통합 체크리스트: [`docs/frontend-backend-integration-checklist.md`](docs/frontend-backend-integration-checklist.md)
- 이슈 작성 규칙: [`docs/issue-guide.md`](docs/issue-guide.md)
- 브랜치 규칙: [`docs/branch-strategy.md`](docs/branch-strategy.md)
- 커밋 규칙: [`docs/commit-convention.md`](docs/commit-convention.md)
- PR 규칙: [`docs/pull-request-guide.md`](docs/pull-request-guide.md)

## 4. 이슈 범위와 구현 원칙

- `FE`: `frontend/` 중심의 작업
- `BE`: `backend/` 중심의 작업
- `COMMON`: `docs/`, 루트 설정, 공통 협업 규칙 또는 여러 영역에 걸친 작업
- 어떤 scope라도 관련 문서, API 계약, 테스트, 프론트엔드·백엔드 연동 영향을 확인한다.
- 현재 이슈의 범위를 벗어난 기능을 임의로 구현하지 않는다.
- 구현 전 `git status --short --branch`로 기존 변경사항을 확인하고 보존한다.
- 비밀정보, 개인정보, 음성 원문, access token, refresh token을 저장소나 로그에 남기지 않는다.
- 의료적 확정 진단처럼 보이는 문구를 사용자에게 반환하지 않는다.

## 5. 검증

변경 영역에 맞는 검사를 실행하고 결과를 이슈 또는 PR에 기록한다.

```bash
# Backend
cd backend && ./gradlew test && ./gradlew build

# Frontend
cd frontend && npm run typecheck
```

프론트엔드와 백엔드를 함께 변경한 경우 양쪽의 API 계약과 통합 체크리스트도 함께
확인한다.

## 6. Git 협업

- `main`과 `develop`에 직접 커밋하지 않는다.
- 작업 브랜치는 Issue를 기준으로 `develop`에서 생성한다.
- 브랜치 형식은 `type/scope/#issue-number-short-description`을 따른다.
- 커밋 형식은 `type(scope): 작업 요약`을 따른다.
- PR 대상은 `develop`이다.
- 사용자 요청 없이 원격 push, PR 생성, merge를 하지 않는다.
- `git reset --hard`나 무단 checkout으로 기존 변경사항을 되돌리지 않는다.
