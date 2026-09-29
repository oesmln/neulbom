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

mock 모드는 화면 흐름을 확인하기 위한 미리보기입니다. CIST에서는 `샘플 답변으로 진행`을
사용하며 음성을 전사하지 않습니다. AI 정서 문답의 답변도 샘플이라고 표시됩니다.
실제로 말한 문장이 `음성 인식 결과`에 나타나는지 확인하려면 백엔드와 STT provider를
실행하고 `EXPO_PUBLIC_API_BASE_URL`을 해당 백엔드 주소로 설정해야 합니다.

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

## Android APK 빌드

`EXPO_PUBLIC_API_BASE_URL`에는 `/api/v1`을 제외한 운영 HTTPS 호스트를 설정한다.
카카오·네이버 redirect URI도 각 provider 콘솔에 등록한 값과 일치시킨다.

```bash
npx eas-cli login
npx eas-cli build:configure
npm run eas:build:android:preview
```

`preview` profile은 내부 배포용 APK를 만든다. API 주소나 JavaScript 번들이 바뀌면
APK를 다시 빌드하고 발표 기기에 설치해야 한다.

## 보호자 일기 푸시 시연

보호자 계정으로 실제 iOS·Android 앱에 로그인하면 알림 권한을 요청하고 Expo 푸시 토큰을 서버에 등록한다. AI 대화를 끝내 일기가 생성되면 연결·동의·일기 접근 범위가 유효한 보호자의 기기에 알림을 보낸다. 알림을 누르면 해당 어르신의 일기를 연다.

- EAS 프로젝트를 연결하고 앱 빌드에 프로젝트 UUID를 포함한다. 로컬 개발 빌드에서 자동 인식되지 않으면 `.env`의 `EXPO_PUBLIC_EAS_PROJECT_ID`를 설정한다.
- Android EAS 자격증명에 FCM v1 서비스 계정을, iOS에는 APNs 키를 등록한다. 자격증명 원문은 저장소에 두지 않는다.
- Expo Go와 Web에서는 원격 휴대폰 푸시를 검증할 수 없다. EAS development/preview 빌드를 실제 기기에 설치하고 알림 권한을 허용한다.
- 알림이 오지 않으면 먼저 보호자 계정의 일기 접근 범위, 서버 알림 설정, 기기 등록 응답, Expo 푸시 자격증명을 확인한다.

설정 절차: [Expo 푸시 설정](https://docs.expo.dev/push-notifications/push-notifications-setup/), [Expo Push Service 전송](https://docs.expo.dev/push-notifications/sending-notifications/).

## Web 빌드

```bash
EXPO_PUBLIC_API_BASE_URL=https://api.neulbom.example \
  npx expo export --platform web --output-dir dist
```

운영 Compose에서는 이 빌드를 Nginx 이미지 안에 포함한다. 공개 주소의 `/`은 웹 앱,
`/api/v1`은 Spring Boot API로 연결되며 새로고침한 SPA 경로도 `index.html`로
돌아간다.

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
