# Frontend

React Native 기반 모바일 애플리케이션입니다.

## 실행

```bash
npm install
cp .env.example .env
npm start
```

`.env`의 `EXPO_PUBLIC_API_BASE_URL`에는 `/api/v1`을 제외한 백엔드 주소를
설정합니다. 값을 비우면 mock API로 실행됩니다.

- iOS simulator / Expo Web: `http://localhost:8080`
- Android emulator: `http://10.0.2.2:8080`
- 실제 기기: 같은 네트워크에 연결된 개발 PC의 LAN 주소

CIST의 Q11 recognition plan, 조건부 문항, 비동기 AI 분석과 재녹음 재시도를 실제 서버로 확인하려면 [`../docs/cist-ai-local-integration-test.md`](../docs/cist-ai-local-integration-test.md)를 따릅니다.

Metro 서버 실행 후 별도 터미널에서 iOS 또는 Android 앱을 실행합니다.

```bash
npm run ios
# 또는
npm run android
```

## 발표용 Android APK

`EXPO_PUBLIC_API_BASE_URL`에는 `/api/v1`을 제외한 운영 HTTPS 호스트를 설정한다.
카카오·네이버 redirect URI도 각 provider 콘솔에 등록한 값과 일치시킨다.

```bash
npx eas-cli login
npx eas-cli build:configure
npm run eas:build:android:preview
```

`preview` profile은 내부 배포용 APK를 만든다. API 주소나 JavaScript 번들이 바뀌면
APK를 다시 빌드하고 발표 기기에 설치해야 한다.

## 오프라인 녹음 재전송

답변 녹음은 업로드 전에 앱 영속 저장소에 복사합니다. Native는 앱 전용 문서 디렉터리, Web은 IndexedDB를 사용하며 queue metadata에 음성 내용이나 사용자 이름을 기록하지 않습니다.

- 로그인 세션 복원, 네트워크 재연결, 앱 활성화 때 현재 사용자의 queue만 순차 전송합니다.
- 모든 재시도는 처음 만든 `client_recording_id`와 `Idempotency-Key`를 유지합니다.
- 성공한 음성 파일과 queue metadata는 즉시 삭제하고, 화면 복원을 위한 최소 완료 receipt도 소비 후 삭제합니다.
- 다른 계정의 항목과 7일이 지난 항목은 전송하지 않고 로컬에서 정리합니다.
- 원본 음성, 업로드 body, token은 로그에 남기지 않습니다.

## 주요 역할

- 사용자·보호자 화면 구현
- CIST 검사 및 인지기능 진단 게임 UI 구현
- AI 정서 문답, 일기, 캠페인, 알림 화면 구현
- Spring Boot API 연동
