# Issue Guide

작업은 Issue 생성으로 시작합니다.

## Issue Title

형식:

```text
[SCOPE] type: 작업 요약
```

예시:

```text
[FE] feature: 로그인 페이지 구현
[BE] feature: 사용자 API 구현
[BE] fix: 토큰 만료 시 재로그인 처리
[COMMON] docs: README 실행 방법 추가
```

## Scopes

- `fe`: 프론트엔드 작업
- `be`: 백엔드 작업
- `common`: 공통 설정, 문서, GitHub 템플릿, 레포 관리 작업

Issue 제목에서는 scope를 대문자로 작성합니다.
작업 요약은 한글로 작성합니다.

- `FE`: 프론트엔드 작업
- `BE`: 백엔드 작업
- `COMMON`: 공통 설정, 문서, GitHub 템플릿, 레포 관리 작업

## Issue Content

Issue에는 아래 내용을 포함합니다.

- 작업 목적
- 구현 범위
- 완료 조건
- 참고 자료 또는 화면

## Labels

가능하면 작업 성격에 맞는 라벨을 붙입니다.

- `feature`
- `fix`
- `docs`
- `refactor`
- `test`
- `chore`
- `fe`
- `be`
- `common`
