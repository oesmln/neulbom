# V25 마이그레이션 운영 복구 (#209)

2026-09-29 운영 자동 배포에서 백엔드가 Flyway 검증 오류로 시작하지 못했다.
운영 DB의 V25 이력은 `V25__add_daily_cognitive_trend_schema.sql`이고
체크섬은 `-312812440`이다. 이 마이그레이션이 만든
`cognitive_feature_snapshots`와 `daily_cognitive_estimates` 테이블은
운영 DB에 존재한다. 이후 커밋에서 다른 SQL을 V25로 재사용해 검증이 실패했다.

복구 소스는 적용된 V25를 원문 그대로 보존한다. V26은 기준 분석 연결,
V27은 AI 작업 유형, V28은 `cist_ai_analyses.feature_snapshot` 컬럼을 추가한다.
기존 V25가 적용된 DB와 빈 DB 모두 이 순서로 이동한다. 체크섬만 `repair`하면
V28 이전에 필요한 컬럼이 만들어지지 않으므로 사용하지 않는다.

운영 배포 전후에는 VM의 PostgreSQL 컨테이너에서 스키마 이력만 확인한다.
아래 명령은 사용자 데이터나 비밀 값을 출력하지 않는다.

```bash
sudo docker exec neulbom-postgres-1 psql -U neulbom -d neulbom -c \
  "SELECT version, script, checksum, success FROM flyway_schema_history WHERE version IN ('25', '26', '27', '28') ORDER BY installed_rank;"
sudo docker exec neulbom-postgres-1 psql -U neulbom -d neulbom -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name = 'cist_ai_analyses' AND column_name = 'feature_snapshot';"
```

배포 후 V25는 위 원래 스크립트·체크섬으로 남고 V26~V28이 성공으로 기록되어야
한다. 마지막 조회에는 `feature_snapshot` 한 행이 나와야 한다. 백엔드
`/actuator/health/readiness`와 자동 배포 워크플로의 성공도 확인한다.
