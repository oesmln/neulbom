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

Metro 서버 실행 후 별도 터미널에서 iOS 또는 Android 앱을 실행합니다.

```bash
npm run ios
# 또는
npm run android
```

## 주요 역할

- 사용자·보호자 화면 구현
- CIST 검사 및 인지기능 진단 게임 UI 구현
- AI 정서 문답, 일기, 캠페인, 알림 화면 구현
- Spring Boot API 연동
