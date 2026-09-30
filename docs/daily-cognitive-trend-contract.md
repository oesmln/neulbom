# 일상 문답 기반 인지 추이 분석 계약

이 문서는 전체 CIST 기준 분석과 `emotional_qa` 부분 갱신 분석 사이의 계약을 정리한다. 정확한 AI 서버 요청·응답 스키마는 [`ai-server-openapi-v1.yaml`](../ai-server/contracts/ai-server-openapi-v1.yaml), 앱 API 경로는 [`backend/docs/api-spec.md`](../backend/docs/api-spec.md)를 기준으로 한다. 두 결과는 인지저하 위험 신호를 살피는 참고용 스크리닝 결과이며 의학적 진단이나 공식 CIST 점수가 아니다.

## 입력과 기준 스냅샷

- 전체 CIST(`cist`·`baseline`·`onboarding`) 분석은 기존 17개 문항 계약을 유지한다. 조건부 문항 중 시행하지 않은 문항도 `not_applicable` 슬롯으로 포함한다.
- 전체 분석이 `completed`이고 `result.feature_snapshot`이 있으면 해당 분석의 기준 스냅샷으로 저장한다. 스냅샷에는 계약·모델 버전, 원본 `model_score`, 시행 문항의 AST·KcELECTRA 특징, 17개 문항의 `wrong_event`·응답 지연 관측값, 재집계된 Fusion 특징이 포함된다. 시행하지 않은 문항의 관측값은 `null`일 수 있다. 스냅샷 계약 이전에 저장된 완료 결과는 원본 `model_score`만 보호자 위험 추이에 사용하며 없는 문항 특징을 생성하지 않는다. 이 결과는 일상 부분 갱신의 기준이 될 수 없다.
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

백엔드는 일상 세션 시작 전에 **검사 세션이 종료된** 최신 전체 CIST의 완료된 AI 분석을 `baseline_analysis_id`로 선택한다. CIST AI 분석이 일상 세션 시작 이후에 완료돼도 해당 일상 세션의 분석을 나중에 생성할 수 있다. 최신 CIST 분석이 아직 처리 중이거나 특징 스냅샷이 없으면 이전 CIST의 스냅샷으로 되돌아가지 않고 일상 부분 갱신을 기다리거나 생성하지 않는다. 같은 기준 분석에 연결된 직전 완료 일상 분석이 있으면 그 `feature_snapshot`을 `input_snapshot`으로 사용하고, 없으면 기준 분석의 스냅샷을 사용한다. 새 전체 CIST 검사가 끝나면 이후 세션은 새 기준에 연결되며 이전 계보의 일상 스냅샷을 이어받지 않는다. 기존 기준·일상 결과는 덮어쓰지 않는다. 같은 세션의 분석 생성은 기존 분석을 반환한다.

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

백엔드는 일상 세션 종료 후 분석을 자동 시작하고 진행 중 상태를 서버에서 동기화한다. 종료 시점에 기준 CIST 스냅샷이 아직 없으면, 스냅샷이 준비된 뒤 분석 기록이 없는 일상 세션을 복구 작업에서 다시 시도한다. 전체 CIST는 검사 화면에서 분석 생성을 요청하며, 최초 생성 요청이 누락된 종료 세션을 서버 복구 작업에서 다시 찾는다. 앱 API는 분석 ID·상태·재시도 여부 등 **상태만** 반환하며 모델 점수나 스냅샷을 고령자 화면에 직접 노출하지 않는다. 일상 `output_snapshot`과 AI 원본 결과는 백엔드의 분석 기록에 보존한다.

## 표시 및 통합 범위

백엔드는 완료된 전체 CIST 분석 중 특징 스냅샷이 있는 결과를 `cognitive_feature_snapshots`에 기준 스냅샷으로 저장하고, 종료된 일상 문답 세션의 부분 갱신 결과를 `daily_cognitive_estimates`에 저장한다. 진행 중인 분석 상태는 서버에서 동기화한다. 일상 분석은 동의, 녹음·STT, 완료된 CIST 기준 스냅샷 등 앞 절의 조건을 충족해야 생성되므로, 모든 일상 문답에서 추정점이 생기는 것은 아니다.

보호자 `GET /analysis/cognitive/{user_id}/history`와 `GET /guardian/{guardian_id}/report`는 완료된 전체 CIST 결과와 저장된 일상 추정치를 `ai_risk_trend_points[]`에 반환한다. 각 점에는 서울 시간 기준 `date`, 0~1의 `risk_score`, `point_type`(`full_cist` 또는 `daily_partial_estimate`), `is_estimated`, `analyzed_at`, `session_id`, `baseline_session_id`, `baseline_snapshot_id`가 있다. 기존 전체 CIST 중 스냅샷 저장 이전에 생성된 점의 `baseline_snapshot_id`는 `null`일 수 있다. 프론트엔드는 `risk_score × 100`을 반올림한 **0~100 위험 신호 지수**로 표시하며 발병 확률이나 공식 CIST 30점 점수로 표현하지 않는다.

### 조회 기간과 이전 기준점

- 보호자 상세 화면은 `1개월`, `3개월`, `6개월`, `1년`, `전체` 기간과 `전체 추이`·`CIST 검사만` 보기를 제공한다. 기간의 양 끝 날짜는 `Asia/Seoul` 달력 날짜로 포함하며, `전체`는 시작일 없이 오늘까지 조회한다.
- `ai_risk_trend_points[]`와 그래프에는 선택 기간 안의 점만 둔다. 백엔드는 `from_date` 이전의 가장 최근 완료 CIST를 동일한 점 구조의 `prior_cist_baseline`으로 별도 반환한다. 이 점은 그래프에 추가하지 않고 날짜·지수를 **조회 기간 이전 기준점** 카드로 보여준다. 기간 안에 CIST가 없으면 현재 적용 중인 기준점, 새 CIST가 있으면 조회 시작 시 기준점으로 설명한다.
- `CIST 검사만` 보기는 일상 추정점을 숨긴다. 기간 안에 CIST가 없으면 “이 기간의 CIST 검사는 없어요”와 마지막 검사 기준점 카드를 보여준다. 이전 검사가 있으면 `기간 넓혀 보기`가 그 검사를 포함하는 가장 짧은 상위 기간을 선택하고, 1년에도 포함되지 않으면 `전체`로 이동한다. 완료된 CIST 자체가 없으면 마지막 검사 카드와 버튼을 표시하지 않는다.

### 재검사와 변화 설명

- 전체 CIST 점은 진한 점과 실선, 일상 추정점은 테두리 점과 점선으로 구분한다. CIST 재검사는 `새 기준점`으로 표시한다. 이전 기준에 속한 일상 점선은 새 CIST 점으로 이어 붙이지 않으며, 이후 일상 점은 새 `baseline_snapshot_id`(기존 기록은 `baseline_session_id`) 계보에서 시작한다.
- 변화 설명은 **이전 CIST → 최근 CIST**와 **최근 CIST 기준점 → 최근 일상 문답 추정**을 분리한다. 조회 기간 이전 기준점도 최근 CIST와의 검사 간 비교에는 사용할 수 있지만 그래프 점에는 넣지 않는다. 일상 변화는 최근 CIST 계보의 추정점이 있을 때만 계산한다. `CIST 검사만` 보기에서는 일상 변화 설명을 숨긴다.
- 보호자 대시보드와 상세 화면은 `cognitive_analyses` 기반 0~30점 인지 점수 추이, 평균 점수, 정상 하한선을 표시하지 않는다. 이 API의 기존 `trend_points[]`와 인지 점수 필드는 남아 있지만 보호자 AI 위험 신호 그래프의 데이터가 아니다. 대시보드는 일상 문답 값이 CIST 기준점에서 추정된다고 안내하고, 상세 화면은 일부 문항만 갱신한 결과라고 설명한다. 두 화면 모두 AI 위험 신호가 진단 결과가 아님을 밝힌다.
