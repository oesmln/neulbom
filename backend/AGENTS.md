# AGENTS.md

이 문서는 저장소 루트의 [`AGENTS.md`](../AGENTS.md)를 상속하며, `backend/`에만 추가로 적용되는 백엔드 개발 규칙이다.

## 1. 프로젝트 목표

늘봄은 고령자가 AI 캐릭터와 대화하고 음성·텍스트 활동을 진행하면서 인지 건강 변화를 꾸준히 관찰할 수 있도록 돕는 성장형 캐릭터 기반 치매 조기 스크리닝 서비스다.

핵심 사용자와 기능은 다음과 같다.

- `elder`: 검사, AI 정서 문답, 일기, 게임, 캐릭터, 캠페인, 알림을 사용하는 고령자
- `guardian`: 동의가 완료된 고령자의 검사 결과, 활동, 일기와 위험 추이를 확인하는 보호자
- 보호자 역할은 기관 또는 검사 목적의 대상자 조회가 필요한 경우에도 별도 역할을 만들지 않고 `guardian`으로 처리한다.
- CIST 기반 인지 선별검사와 AI 정서 문답 세션
- 문항 단위 음성 녹음, 오프라인 재전송, STT·음향·인지 분석
- 검사 결과, 세션 요약, 일기·캘린더, 미니게임·캐릭터, 지역 캠페인, 알림

검사 및 AI 분석 결과는 의료적 진단이 아니다. 사용자에게는 인지기능 저하 의심 신호, 추가 확인 권장, 스크리닝 참고 점수와 같이 관찰을 돕는 표현만 제공한다.

## 2. 기준 문서

구현 전 다음 문서를 먼저 확인한다.

- API 정의: [`docs/api-spec.md`](docs/api-spec.md)
- 개발 순서와 API 체크리스트: [`docs/backend-development-checklist.md`](docs/backend-development-checklist.md)
- 저장소 브랜치 규칙: [`../docs/branch-strategy.md`](../docs/branch-strategy.md)
- 커밋 규칙: [`../docs/commit-convention.md`](../docs/commit-convention.md)
- 이슈 작성 규칙: [`../docs/issue-guide.md`](../docs/issue-guide.md)
- PR 작성 규칙: [`../docs/pull-request-guide.md`](../docs/pull-request-guide.md)

API의 경로, 필드, 상태값, HTTP 상태 코드는 `api-spec.md`를 기준으로 한다. 명세를 변경하면 관련 체크리스트와 테스트도 함께 수정한다. 명세서와 코드가 다르면 임의로 해석하지 말고 이슈에 차이를 기록한다.

## 3. 기술 스택

- Language: Java 21
- Framework: Spring Boot 3.5 계열
- Build: Gradle Wrapper
- Web: Spring MVC
- Validation: Jakarta Bean Validation
- Security: Spring Security, JWT 기반 인증
- Database: PostgreSQL
- Database access: Spring Data JPA와 Hibernate
- Connection pool: HikariCP
- API documentation: springdoc OpenAPI
- Test: JUnit 5, Spring Boot Test, MockMvc
- File storage: 로컬 파일 시스템 또는 MinIO(개발), Object Storage(운영)
- External AI/STT: OpenAI/local Whisper, Google Cloud STT, AST, KcELECTRA, Gemini API

버전은 `build.gradle`과 `gradle-wrapper.properties`에 고정한다. 팀 합의 없이 Spring Boot, Java, 주요 라이브러리 버전을 변경하지 않는다.

## 4. 패키지 규칙

```text
backend/
  AGENTS.md
  docs/                         백엔드 API와 개발 문서
  src/main/java/com/neulbom/backend/
    config/                     환경, 보안, OpenAPI, CORS 설정
    common/                     오류, 응답, 요청 ID, 페이지, 공통 유틸리티
    auth/                       회원가입, 로그인, JWT, refresh token
    user/                       사용자와 온보딩 정보
    guardian/                   보호자 연결과 동의 범위
    session/                    검사·정서 문답·게임 세션
    recording/                  음성 업로드와 오프라인 동기화
    analysis/                   STT·AST·KcELECTRA·스크리닝 분석
    diary/                      일기와 보호자 반응
    game/                       미니게임 결과와 캐릭터 경험치
    campaign/                   지역 건강 캠페인
    notification/               서비스 알림
  src/main/resources/
    application.yml
    application-local.yml
    application-dev.yml
    application-prod.yml
  src/test/                     단위·통합 테스트
```

새 코드는 역할에 맞는 도메인 패키지에 둔다.

- Controller는 HTTP 요청·응답과 입력 검증만 담당한다.
- Service는 업무 규칙과 상태 전이를 담당한다.
- Repository는 DB 접근만 담당한다.
- DTO는 API 요청·응답을 표현하며 Entity를 외부에 직접 노출하지 않는다.
- 외부 AI·저장소·알림 연동은 adapter 또는 integration 계층으로 격리한다.
- Controller에 SQL이나 여러 도메인의 업무 규칙을 직접 작성하지 않는다.

## 5. API 구현 규칙

### 5.1 공통 경로와 인증

- 업무 API의 prefix는 `/api/v1`이다.
- health check는 `/health/**` 또는 `/actuator/health`, OpenAPI 문서는 `/docs` 경로에 둔다.
- 공개 API는 명세서에 공개로 표시된 인증·회원가입·로그인·토큰 갱신 API만 허용한다.
- 인증이 필요한 API는 `Authorization: Bearer {access_token}`을 사용한다.
- `guardian`은 연결(`guardian_links`)과 동의(`consents`)가 모두 유효한 대상자만 조회할 수 있다.
- `user_id`, `guardian_id`를 요청으로 받더라도 JWT 주체와 소유권·역할을 서버에서 다시 검증한다.
- 앱이 직접 AI provider를 호출하지 않는다. STT·AST·KcELECTRA·Gemini 작업은 서버 작업으로 실행한다.

### 5.2 요청과 응답

- 명세서에 정의된 필드명과 HTTP 메서드를 그대로 사용한다.
- 성공 응답은 endpoint별 명세 본문을 사용하며 임의의 envelope을 추가하지 않는다.
- 오류 응답은 다음 형식을 사용한다.

```json
{
  "error": "요청 필드가 올바르지 않습니다.",
  "code": 400,
  "detail": "password must be at least 8 characters",
  "request_id": "req_01J..."
}
```

- `400`: 필수값, enum, 날짜, 형식 오류
- `401`: 인증 토큰 누락·만료·위조
- `403`: 역할, 소유권, 연결, 동의 권한 없음
- `404`: 리소스 없음
- `409`: 중복 요청, 중복 연결, 중복 `client_recording_id`
- `413`: 업로드 용량 초과
- `422`: 형식은 맞지만 업무 규칙 위반
- `500`: 처리되지 않은 내부 오류
- `503`: 외부 AI 또는 비동기 분석 서비스 일시 중단

### 5.3 공통 값과 목록

- ID는 애플리케이션 내부에서 UUID를 사용하고 API에서는 명세서의 문자열 표현을 따른다.
- 서버 저장 timestamp는 UTC를 기준으로 하고 응답에는 ISO 8601과 timezone을 포함한다.
- 목록 API는 `page` 1부터 시작, `limit` 기본 20·최대 100, `from_date`, `to_date`를 공통 검증한다.
- 페이지 응답은 `items`, `total`, `page`, `limit`, `has_next` 구조를 필요한 목록 API에 사용한다.
- 날짜, enum, 페이지 범위, 파일 형식 오류는 어떤 endpoint에서도 같은 오류 코드와 상태로 반환한다.

### 5.4 음성 파일과 중복 요청

- 업로드 파일은 확장자, MIME type, 실제 파일 내용, 최대 용량을 모두 검증한다.
- 음성 원문과 건강 관련 분석 결과는 필요한 사용자·보호자 권한에만 반환한다.
- 오프라인 녹음은 `client_recording_id`를 기준으로 멱등성을 보장한다.
- 같은 `client_recording_id`를 재전송해도 녹음이 중복 생성되지 않는다.
- 비용이나 외부 부작용이 있는 요청은 명세서에 따라 `Idempotency-Key`를 지원한다.

## 6. 보안 및 개인정보

- JWT secret, DB password, AI API key, Object Storage secret을 Git에 커밋하지 않는다.
- `.env`와 실제 환경 설정은 커밋하지 않고 `.env.example`에는 변수명과 안전한 예시값만 둔다.
- 비밀번호는 평문으로 저장하거나 로그에 출력하지 않는다.
- 음성 원문, 건강 정보, 분석 원문, access token, refresh token, 개인정보를 로그에 남기지 않는다.
- 로그에는 `request_id`, 작업 ID, 대상 리소스 ID, task type, 모델명, prompt version, 처리 시간과 provider 상태 같은 비민감 정보만 기록한다.
- 공개 API는 필요한 컬럼만 명시적으로 반환한다.
- 보호자의 대상자 데이터 접근 전에 역할·연결·동의·접근 범위를 모두 검사한다.
- 사용자에게 의료적 확정 진단처럼 보이는 문구를 반환하지 않는다.
- CORS 허용 origin은 환경별 설정으로 분리한다.
- 파일 저장소의 원본 URL과 임시 다운로드 URL을 외부에 불필요하게 노출하지 않는다.

## 7. 실행 및 테스트

로컬 PostgreSQL과 필요한 환경변수를 준비한 뒤 실행한다.

```bash
cd backend
cp .env.example .env
./gradlew bootRun --args='--spring.profiles.active=local'
```

변경을 완료하기 전에 다음 검사를 실행한다.

```bash
cd backend
./gradlew test
./gradlew build
```

확인할 항목:

- 로컬 profile로 PostgreSQL에 연결된다.
- `/health` 또는 `/actuator/health`가 정상 응답한다.
- 잘못된 요청이 공통 오류 JSON으로 반환된다.
- request ID가 응답과 로그에 연결된다.
- OpenAPI 문서에서 controller와 schema를 확인할 수 있다.
- 정상 요청, 검증 실패, 권한 실패, 리소스 없음, 외부 provider 실패를 테스트한다.
- 테스트와 빌드 결과에 비밀정보가 출력되지 않는다.

## 8. 완료 조건

작업은 코드만 작성한 상태가 아니라 다음 조건을 모두 만족해야 완료로 본다.

- API 명세의 경로·메서드·필드·상태 코드와 구현이 일치한다.
- 관련 DTO 검증, 권한 검증, 예외 처리가 포함되어 있다.
- 필요한 migration과 테스트가 포함되어 있다.
- 외부 연동은 timeout, retry, 실패 상태를 처리한다.
- 로그에 개인정보·음성 원문·토큰이 없다.
- 새로운 환경변수는 `.env.example`에 기록되어 있다.
- `./gradlew test`와 `./gradlew build`가 통과한다.
- 문서와 체크리스트가 실제 구현 상태를 반영한다.

## 9. Git 협업 규칙

- 작업은 반드시 Issue를 만든 뒤 `develop`에서 작업 브랜치를 생성한다.
- `main`과 `develop`에 직접 커밋하지 않는다.
- 브랜치 형식은 `type/scope/#issue-number-short-description`을 따른다.
- 백엔드 scope는 저장소 브랜치 전략에 따라 `be`를 사용한다.
- 커밋 형식은 `type(scope): 작업 요약`을 사용한다. 예: `feature(be): 공통 오류 처리 구현`
- 한 커밋에는 하나의 논리적 의도만 담는다.
- API 코드와 테스트는 가능하면 같은 작업 단위로 작성한다.
- PR 대상은 항상 `develop`이다.
- PR 본문에 변경 사항, 테스트, 관련 이슈(`close #번호`), 참고 사항을 기록한다.
- 사용자 요청 없이 원격 push, PR 생성, merge를 하지 않는다.
- 기존 사용자 변경사항을 덮어쓰거나 `reset --hard`, 무단 checkout으로 되돌리지 않는다.

## 10. 작업 규칙

- 작업 시작 전 이 문서, API 명세서, 개발 체크리스트, 관련 이슈를 읽는다.
- 현재 이슈의 범위를 벗어난 기능을 임의로 구현하지 않는다.
- 명세서에 없는 endpoint, 상태값, 응답 필드를 임의로 추가하지 않는다.
- 구현 전에 기존 변경사항과 `git status`를 확인하고, 다른 작업자의 변경을 보존한다.
- 공통 기능은 `common` 또는 `config`에 두고 도메인 패키지에 중복 구현하지 않는다.
- API 변경 시 API 명세서, 체크리스트, OpenAPI schema, 테스트를 함께 확인한다.
- 테스트가 실패하면 원인을 해결한 뒤 완료로 표시한다.
- 임시 파일, 디버그 출력, 실제 secret, 개인정보 샘플을 저장소에 남기지 않는다.
- 구현 범위와 완료 조건은 현재 GitHub Issue를 기준으로 하며, API 명세서와 개발 체크리스트에서 해당 항목의 선행 작업·구현 상태를 확인한다.
