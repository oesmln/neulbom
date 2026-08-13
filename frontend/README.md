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

## 카카오·네이버 로그인

소셜 로그인은 `expo-auth-session`으로 provider의 일회성 authorization code만 받은 뒤 `POST /auth/oauth/{provider}`로 전달합니다. provider client secret과 access token은 앱에 저장하지 않습니다.

`.env`에는 provider 콘솔에 등록한 공개 client ID와 redirect URI를 설정합니다.

```dotenv
EXPO_PUBLIC_KAKAO_CLIENT_ID=카카오_REST_API_키
EXPO_PUBLIC_KAKAO_REDIRECT_URI=http://localhost:8081/auth/callback/kakao
EXPO_PUBLIC_NAVER_CLIENT_ID=네이버_CLIENT_ID
EXPO_PUBLIC_NAVER_REDIRECT_URI=http://localhost:8081/auth/callback/naver
```

- 로컬 Web은 Metro 실행 포트와 callback 포트를 일치시킵니다.
- Kakao REST API redirect URI는 HTTP/HTTPS만 등록할 수 있습니다. Native 배포는 앱과 연결된 HTTPS universal link를 사용합니다.
- Native OAuth는 custom scheme을 쓸 수 있는 development build 또는 universal link가 연결된 배포 build에서 검증합니다. Expo Go에서는 검증하지 않습니다.
- 같은 redirect URI를 백엔드의 `KAKAO_ALLOWED_REDIRECT_URIS`, `NAVER_ALLOWED_REDIRECT_URIS`에도 등록합니다.
- authorization endpoint는 기본 공식 주소를 사용하며, 별도 환경에서만 `EXPO_PUBLIC_*_AUTHORIZATION_ENDPOINT`로 덮어씁니다.

## 주요 역할

- 사용자·보호자 화면 구현
- CIST 검사 및 인지기능 진단 게임 UI 구현
- AI 정서 문답, 일기, 캠페인, 알림 화면 구현
- Spring Boot API 연동
