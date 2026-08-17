# AGENTS.md

이 문서는 늘봄(NEULBOM) 저장소 전체에 적용되는 공통 규칙이다. 이슈 분석과
구현 범위는 `backend/`가 아니라 저장소 루트(`.`)를 기준으로 판단한다.

## 1. 적용 범위

- 기본 탐색 범위는 저장소 전체다.
- 이슈를 분석할 때 `frontend/`, `backend/`, `docs/`, 루트 설정 파일을 모두 확인한다.
- 이슈 파서와 파일 검색 루트를 `backend/`로 하드코딩하지 않는다.
- `backend/AGENTS.md`는 이 문서와 함께 적용되는 백엔드 전용 추가 규칙이다.
- 하위 디렉터리에 별도의 `AGENTS.md`가 있으면 저장소 전체 규칙과 함께 적용한다.

저장소 루트에서 Git에 등록된 전체 파일을 확인할 때는 다음 명령을 사용한다.

```bash
git ls-files
```

로컬 파일을 재귀적으로 확인할 때는 생성물과 의존성 디렉터리만 제외한다. `docs/`와
루트 파일은 이슈 분석 대상에서 제외하지 않는다.

- `.git/`
- `backend/build/`
- `backend/.gradle/`
- `backend/uploads/`
- `frontend/node_modules/`
- `frontend/dist/`
- `.env` 및 실제 비밀정보가 포함된 로컬 설정 파일

## 2. 기준 문서

구현 전에 관련 문서를 확인하고, 문서와 구현이 다르면 이슈 또는 PR에 차이를 기록한다.

- 이슈 작성: [`docs/issue-guide.md`](docs/issue-guide.md)
- 브랜치 전략: [`docs/branch-strategy.md`](docs/branch-strategy.md)
- 커밋 규칙: [`docs/commit-convention.md`](docs/commit-convention.md)
- PR 작성: [`docs/pull-request-guide.md`](docs/pull-request-guide.md)
- API 정의: [`backend/docs/api-spec.md`](backend/docs/api-spec.md)
- 백엔드 개발 체크리스트: [`backend/docs/backend-development-checklist.md`](backend/docs/backend-development-checklist.md)
- 프론트엔드·백엔드 통합 체크리스트: [`docs/frontend-backend-integration-checklist.md`](docs/frontend-backend-integration-checklist.md)

## 3. Issue 규칙

작업은 Issue 생성으로 시작한다.

- 제목 형식: `[SCOPE] type: 작업 요약`
- 제목의 scope는 대문자로 작성한다: `FE`, `BE`, `COMMON`
- 작업 요약은 한글로 작성한다.
- Issue 본문은 `작업 목적`, `구현 범위`, `완료 조건`, `참고 자료 또는 화면` 구조를 유지한다.
- 실행·검증·문서화 항목은 `- [ ]` 체크리스트로 작성한다.
- 확인된 항목만 `- [x]`로 표시한다.
- 관련 성격에 따라 `feature`, `fix`, `docs`, `refactor`, `test`, `chore`, `fe`, `backend`, `common` 라벨을 사용한다.

## 4. 브랜치 규칙

- `main`은 배포 가능한 안정 버전만 유지하며 직접 커밋하지 않는다.
- `develop`은 기본 개발 브랜치다.
- 모든 작업 브랜치는 Issue 생성 후 `develop`에서 만든다.
- 브랜치 형식은 `type/scope/#issue-number-short-description`을 따른다.
- scope는 `fe`, `be`, `common` 중 하나를 사용한다.
- type은 `feature`, `fix`, `docs`, `refactor`, `test`, `chore` 중 하나를 사용한다.
- 모든 PR의 대상 브랜치는 기본적으로 `develop`이다.

예시:

```text
feature/fe/#12-login-page
fix/be/#18-token-refresh
docs/common/#21-update-readme
```

## 5. 커밋 규칙

- 형식은 `type(scope): 작업 요약`이다.
- type과 scope는 정해진 영문 값을 사용한다.
- 작업 요약은 한글로 간결하게 작성한다.
- 한 커밋에는 하나의 논리적 의도만 담는다.
- `frontend/`와 `backend/`를 함께 수정한 경우 가능하면 커밋을 나눈다.
- 관련 Issue가 있으면 커밋 본문 또는 PR 설명에 연결한다.

## 6. Pull Request 규칙

- PR은 작업 브랜치에서 `develop`으로 생성한다.
- 제목 형식은 `[SCOPE] type: 작업 요약`이다.
- 제목의 scope는 대문자, 작업 요약은 한글로 작성한다.
- 본문은 다음 형식을 사용한다.

```markdown
## 변경 사항
-

## 테스트
-

## 관련 이슈
- close #이슈번호

## 참고 사항
-
```

- `관련 이슈`에는 `close #이슈번호` 형식으로 Issue를 연결한다.
- 대상 브랜치가 `develop`인지, 직접 테스트했는지 확인한다.
- 불필요한 로그·주석·임시 파일을 제거한다.
- 문서 수정이 필요한 경우 함께 반영한다.
- 최소 1명 이상의 리뷰 후 `Squash and merge` 방식으로 머지한다.

## 7. 작업 및 검증 원칙

- 구현 전 `git status --short --branch`로 기존 변경사항을 확인하고 보존한다.
- 이슈 scope가 특정 영역이어도 관련 공통 문서와 프론트엔드·백엔드 연동 영향을 확인한다.
- API 변경은 API 명세, 체크리스트, 관련 테스트를 함께 확인한다.
- 비밀정보, 개인정보, 음성 원문, 토큰을 저장소나 로그에 남기지 않는다.
- 사용자 요청 없이 원격 push, PR 생성, merge를 하지 않는다.
- 기존 변경사항을 덮어쓰거나 `git reset --hard`, 무단 checkout으로 되돌리지 않는다.
- 변경 영역에 맞는 테스트와 타입체크를 실행하고 결과를 PR에 기록한다.
