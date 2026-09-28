from copy import deepcopy
from pathlib import Path
from uuid import UUID, uuid4

from app.repositories.analysis import (
    AnalysisStatus,
)
from tests.test_analysis_api import (
    SERVICE_TOKEN,
    create_request_payload,
    create_test_client,
    headers,
)
from tests.test_daily_analysis_schemas import (
    daily_create_payload,
    daily_result_payload,
)


def auth_headers() -> dict[str, str]:
    return {
        "Authorization": (
            f"Bearer {SERVICE_TOKEN}"
        ),
    }


def test_creates_and_gets_pending_daily_analysis(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        repository,
        _contracts,
    ) = create_test_client(tmp_path)
    payload = daily_create_payload()

    created = client.post(
        "/v1/daily-cognitive-analyses",
        headers=headers(
            "daily-analysis-create-key-0001",
        ),
        json=payload,
    )
    fetched = client.get(
        "/v1/daily-cognitive-analyses/"
        f"{payload['analysis_id']}",
        headers=auth_headers(),
    )

    assert created.status_code == 202
    assert created.json()["session_id"] == (
        payload["session_id"]
    )
    assert created.headers["location"] == (
        "/v1/daily-cognitive-analyses/"
        f"{payload['analysis_id']}"
    )
    assert fetched.status_code == 200
    assert fetched.json()["status"] == "pending"
    assert fetched.headers["retry-after"] == "2"
    assert worker.enqueued_ids == [
        UUID(payload["analysis_id"]),
    ]
    stored = repository.get(
        UUID(payload["analysis_id"]),
    )
    assert stored is not None
    assert stored.request_body[
        "analysis_type"
    ] == "daily_partial_update"


def test_daily_create_is_idempotent(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        _repository,
        _contracts,
    ) = create_test_client(tmp_path)
    payload = daily_create_payload()
    request_headers = headers(
        "daily-analysis-create-key-0002",
    )

    first = client.post(
        "/v1/daily-cognitive-analyses",
        headers=request_headers,
        json=payload,
    )
    second = client.post(
        "/v1/daily-cognitive-analyses",
        headers=request_headers,
        json=payload,
    )

    assert first.status_code == 202
    assert second.status_code == 202
    assert first.json() == second.json()
    assert len(worker.enqueued_ids) == 1


def test_gets_completed_daily_result(
    tmp_path: Path,
) -> None:
    (
        client,
        _worker,
        repository,
        _contracts,
    ) = create_test_client(tmp_path)
    payload = daily_create_payload()
    analysis_id = UUID(
        payload["analysis_id"],
    )
    created = client.post(
        "/v1/daily-cognitive-analyses",
        headers=headers(
            "daily-analysis-create-key-0003",
        ),
        json=payload,
    )
    repository.mark_processing(
        analysis_id,
    )
    repository.mark_completed(
        analysis_id=analysis_id,
        result_body=daily_result_payload(),
    )

    fetched = client.get(
        f"/v1/daily-cognitive-analyses/{analysis_id}",
        headers=auth_headers(),
    )

    assert created.status_code == 202
    assert fetched.status_code == 200
    assert fetched.json()["status"] == "completed"
    assert fetched.json()["result"][
        "result_type"
    ] == "daily_partial_estimate"
    assert "retry-after" not in fetched.headers


def test_retries_daily_analysis_with_same_snapshot(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        repository,
        _contracts,
    ) = create_test_client(tmp_path)
    payload = daily_create_payload()
    analysis_id = UUID(
        payload["analysis_id"],
    )
    created = client.post(
        "/v1/daily-cognitive-analyses",
        headers=headers(
            "daily-analysis-create-key-0004",
        ),
        json=payload,
    )
    repository.mark_processing(
        analysis_id,
    )
    repository.mark_needs_retry(
        analysis_id=analysis_id,
        reason_code="AUDIO_URL_EXPIRED",
        retry_items=(
            {
                "question_code": (
                    "orientation_year"
                ),
                "reason_code": (
                    "AUDIO_URL_EXPIRED"
                ),
                "required_action": (
                    "REISSUE_AUDIO_URL"
                ),
            },
        ),
    )
    original_response = payload[
        "responses"
    ][0]
    retry_payload = {
        "reason_code": "AUDIO_URL_EXPIRED",
        "items": [
            {
                "question_code": (
                    "orientation_year"
                ),
                "retry_action": (
                    "REISSUE_AUDIO_URL"
                ),
                "recording_id": (
                    original_response[
                        "recording_id"
                    ]
                ),
                "response_id": (
                    original_response[
                        "response_id"
                    ]
                ),
                "audio": {
                    "signed_url": (
                        "https://storage.example/"
                        "new-daily-year.wav"
                        "?signature=test"
                    ),
                    "expires_at": (
                        "2099-01-02T00:00:00Z"
                    ),
                    "content_type": "audio/wav",
                    "size_bytes": 1024,
                },
            },
        ],
    }

    retried = client.post(
        "/v1/daily-cognitive-analyses/"
        f"{analysis_id}/retry",
        headers=headers(
            "daily-analysis-retry-key-0001",
        ),
        json=retry_payload,
    )

    assert created.status_code == 202
    assert retried.status_code == 202
    stored = repository.get(
        analysis_id,
    )
    assert stored is not None
    assert stored.status == AnalysisStatus.PENDING
    assert stored.request_body[
        "input_snapshot"
    ] == payload["input_snapshot"]
    assert stored.request_body[
        "responses"
    ][0]["audio"]["signed_url"].startswith(
        "https://storage.example/new-daily-year.wav",
    )
    assert worker.enqueued_ids == [
        analysis_id,
        analysis_id,
    ]


def test_full_and_daily_analysis_routes_are_isolated(
    tmp_path: Path,
) -> None:
    (
        client,
        _worker,
        _repository,
        contracts,
    ) = create_test_client(tmp_path)
    daily_payload = daily_create_payload()
    full_payload = create_request_payload(
        contracts,
    )

    daily_created = client.post(
        "/v1/daily-cognitive-analyses",
        headers=headers(
            "daily-analysis-create-key-0005",
        ),
        json=daily_payload,
    )
    full_created = client.post(
        "/v1/analyses",
        headers=headers(
            "full-analysis-create-key-0005",
        ),
        json=full_payload,
    )
    daily_on_full_route = client.get(
        f"/v1/analyses/{daily_payload['analysis_id']}",
        headers=auth_headers(),
    )
    full_on_daily_route = client.get(
        "/v1/daily-cognitive-analyses/"
        f"{full_payload['analysis_id']}",
        headers=auth_headers(),
    )

    assert daily_created.status_code == 202
    assert full_created.status_code == 202
    assert daily_on_full_route.status_code == 404
    assert full_on_daily_route.status_code == 404


def test_rejects_invalid_daily_question_composition(
    tmp_path: Path,
) -> None:
    (
        client,
        worker,
        _repository,
        _contracts,
    ) = create_test_client(tmp_path)
    payload = daily_create_payload()
    payload["responses"][1] = deepcopy(
        payload["responses"][0],
    )
    payload["responses"][1][
        "recording_id"
    ] = str(uuid4())
    payload["responses"][1][
        "response_id"
    ] = str(uuid4())

    response = client.post(
        "/v1/daily-cognitive-analyses",
        headers=headers(
            "daily-analysis-create-key-0006",
        ),
        json=payload,
    )

    assert response.status_code == 422
    assert worker.enqueued_ids == []
