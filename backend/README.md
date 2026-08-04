# Backend

Spring Boot(Java) 기반 서버/API입니다.

## 실행

```bash
./gradlew bootRun
```

실행 전 PostgreSQL 연결 정보와 Whisper API, Gemini API 등 외부 서비스 설정을 등록해야 합니다.

## 주요 역할

- 사용자 및 보호자 계정 관리
- CIST 검사와 인지기능 진단 게임 결과 관리
- 일기 및 AI 정서 문답 데이터 관리
- 인지 점수와 위험 추이 제공
- PostgreSQL 데이터 저장
- 외부 AI/STT 서비스 연동
