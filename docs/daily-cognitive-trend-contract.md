# 일상 문답 기반 인지 추이 분석 계약

이 문서는 전체 CIST 기준 분석과 `emotional_qa` 부분 갱신 분석 사이의 계약을 정리한다. 정확한 AI 서버 요청·응답 스키마는 [`ai-server-openapi-v1.yaml`](../ai-server/contracts/ai-server-openapi-v1.yaml), 앱 API 경로는 [`backend/docs/api-spec.md`](../backend/docs/api-spec.md)를 기준으로 한다. 두 결과는 인지저하 위험 신호를 살피는 참고용 스크리닝 결과이며 의학적 진단이나 공식 CIST 점수가 아니다.

## 입력과 기준 스냅샷

- 전체 CIST(`cist`·`baseline`·`onboarding`) 분석은 기존 17개 문항 계약을 유지한다. 조건부 문항 중 시행하지 않은 문항도 `not_applicable` 슬롯으로 포함한다.
- 전체 분석이 `completed`이면 AI 서버의 `result.feature_snapshot`을 해당 분석의 `feature_snapshot`으로 저장한다. 스냅샷에는 계약·모델 버전, 원본 `model_score`, 시행 문항의 AST·KcELECTRA 특징, 17개 문항의 `wrong_event`·응답 지연 관측값, 재집계된 Fusion 특징이 포함된다. 시행하지 않은 문항의 관측값은 `null`일 수 있다.
- 일상 세션은 Gemini 질문 5개와 CIST 문제은행 문항 2개로 구성한다. 부분 갱신에 사용하는 문항은 지남력 1개와 주의력 1개다. Gemini 질문·답변은 이 모델의 입력이 아니며, 두 문항을 공식 전체 CIST 점수로 합산하지 않는다.
- 일상 분석은 종료된 본인 `emotional_qa` 세션, 분석·음성 수집 동의, 두 CIST 문항의 답변·녹음·STT, 완료된 이전 전체 CIST 분석과 스냅샷이 있어야 생성할 수 있다.

## 스냅샷 계보와 점수

```text
전체 CIST S0 (기준 점수 B)
  → 첫 일상 분석: S0 입력 → S1 출력 (추정 점수 E1)
  → 다음 일상 분석: S1 입력 → S2 출력 (추정 점수 E2)
새 전체 CIST S0′
  → 이후 일상 분석: S0′ 입력 → 새 계보 시작
```

백엔드는 일상 세션 시작 전에 완료된 최신 전체 CIST를 `baseline_analysis_id`로 선택한다. 같은 기준 분석에 연결된 직전 완료 일상 분석이 있으면 그 `feature_snapshot`을 `input_snapshot`으로 사용하고, 없으면 기준 분석의 스냅샷을 사용한다. 새 전체 CIST가 완료되면 이후 세션은 새 기준에 연결되며 이전 계보의 일상 스냅샷을 이어받지 않는다. 기존 기준·일상 결과는 덮어쓰지 않는다. 같은 세션의 분석 생성은 기존 분석을 반환한다.

AI 서버는 두 문항의 AST·KcELECTRA·오답·응답 지연 특징만 입력 스냅샷에서 교체하고, 전체 Fusion 입력을 재집계해 `output_snapshot`을 만든다. `estimated_model_score`는 이 부분 갱신 벡터의 모델 출력이다. `baseline_model_score`는 해당 전체 CIST의 점수, `input_model_score`는 입력 스냅샷의 점수다.

```text
score_delta_from_baseline = estimated_model_score - baseline_model_score
score_delta_from_previous = estimated_model_score - input_model_score
```

두 변화량은 위험 점수의 차이이며 공식 CIST 점수의 증감이 아니다. 일상 결과의 `risk_flag`와 `risk_level`은 추정 점수에 현재 운영 경계 `0.38592870327757767`, `0.8061380697921943`을 적용한 보조 안내값이다. 일부 문항을 갱신한 결과이므로 독립적인 전체 CIST 검사나 진단 결과로 제시하지 않는다.

## 서버 간 API와 상태

| 단계 | 백엔드 앱 API | AI 서버 내부 API |
| --- | --- | --- |
| 생성 | `POST /api/v1/sessions/{session_id}/cist-ai/daily-analyses` | `POST /v1/daily-cognitive-analyses` |
| 상태 조회 | `GET /api/v1/sessions/{session_id}/cist-ai/daily-analyses` | `GET /v1/daily-cognitive-analyses/{analysis_id}` |
| 재시도 | `POST /api/v1/sessions/{session_id}/cist-ai/daily-analyses/retry` | `POST /v1/daily-cognitive-analyses/{analysis_id}/retry` |

내부 생성 요청에는 `analysis_type=daily_partial_update`, `analysis_id`, `session_id`, `baseline_analysis_id`, 계약·STT 버전, 검사 날짜·시간대, `baseline_model_score`, `input_snapshot`, 시행 문항 응답 2개가 포함된다. AI 서버는 비동기로 `202`를 반환한다. 상태는 `pending`, `processing`, `needs_retry`, `completed`, `failed`이며 `completed`일 때만 `result_type=daily_partial_estimate`, 두 변화량, `updated_question_codes`, `output_snapshot`을 포함한 결과가 존재한다. 재시도는 동일 `analysis_id`를 유지하며 `REISSUE_AUDIO_URL` 또는 `REPLACE_RESPONSE` 규칙을 사용한다. 생성·재시도에는 논리 작업별 `Idempotency-Key`를 사용한다.

백엔드는 세션 종료 후 분석을 자동 시작하고 진행 중 상태를 서버에서 동기화한다. 앱 API는 분석 ID·상태·재시도 여부 등 **상태만** 반환하며 모델 점수나 스냅샷을 고령자 화면에 직접 노출하지 않는다. 일상 `output_snapshot`과 AI 원본 결과는 백엔드의 분석 기록에 보존한다.

## 표시 및 통합 범위

기존 보호자 `ai_risk_trend_points[]`는 현재 완료된 전체 CIST 분석만 반환한다. 일상 추정치를 보호자 그래프에 포함하는 조회·저장·표시 계약은 별도 백엔드/프론트엔드 연동 작업이다. 이를 붙일 때 전체 CIST **기준점**과 일상 **추정점**을 구분하고, 계보·기준 점수·변화량을 보존해야 한다. 기존 0~30 인지 점수 `trend_points[]`와 AI 위험 점수(0~1)를 같은 축에 섞지 않는다.
