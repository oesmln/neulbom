from copy import deepcopy
from pathlib import Path
from uuid import UUID, uuid4

from app.repositories.analysis import AnalysisStatus
from tests.test_analysis_retry_api import (
    CREATE_KEY,
    create_request_payload,
    create_test_client,
    headers,
)

RESTART_KEY = "analysis-restart-key-0001"


def test_restarts_failed_internal_error_with_new_audio_urls(
    tmp_path: Path,
) -> None:
    client, worker, repository, contracts = create_test_client(tmp_path)
    original = create_request_payload(contracts)
    analysis_id = UUID(original["analysis_id"])
    assert client.post(
        "/v1/analyses", headers=headers(CREATE_KEY), json=original
    ).status_code == 202
    repository.mark_processing(analysis_id)
    repository.mark_failed(
        analysis_id=analysis_id, reason_code="INTERNAL_ERROR"
    )

    renewed = deepcopy(original)
    renewed["responses"][0]["audio"]["signed_url"] = (
        "https://storage.example/fresh.wav?signature=test"
    )
    first = client.post(
        f"/v1/analyses/{analysis_id}/restart",
        headers=headers(RESTART_KEY),
        json=renewed,
    )
    repeated = client.post(
        f"/v1/analyses/{analysis_id}/restart",
        headers=headers(RESTART_KEY),
        json=renewed,
    )

    assert first.status_code == 202
    assert repeated.status_code == 202
    assert first.json() == repeated.json()
    stored = repository.get(analysis_id)
    assert stored is not None
    assert stored.status == AnalysisStatus.PENDING
    assert stored.request_body["responses"][0]["audio"]["signed_url"] == (
        "https://storage.example/fresh.wav?signature=test"
    )
    assert worker.enqueued_ids == [analysis_id, analysis_id]
    history = repository.list_archived_attempts(analysis_id)
    assert len(history) == 1
    assert history[0].status == AnalysisStatus.FAILED
    assert history[0].reason_code == "INTERNAL_ERROR"


def test_restart_rejects_other_failure_reasons_and_identifiers(
    tmp_path: Path,
) -> None:
    client, _worker, repository, contracts = create_test_client(tmp_path)
    original = create_request_payload(contracts)
    analysis_id = UUID(original["analysis_id"])
    assert client.post(
        "/v1/analyses", headers=headers(CREATE_KEY), json=original
    ).status_code == 202
    repository.mark_processing(analysis_id)
    repository.mark_failed(
        analysis_id=analysis_id, reason_code="MODEL_UNAVAILABLE"
    )
    rejected = client.post(
        f"/v1/analyses/{analysis_id}/restart",
        headers=headers(RESTART_KEY),
        json=original,
    )
    assert rejected.status_code == 409
    assert repository.get(analysis_id).status == AnalysisStatus.FAILED

    second_id = uuid4()
    repository.create_pending(
        analysis_id=second_id,
        assessment_id=UUID(original["assessment_id"]),
        request_body=original,
    )
    repository.mark_processing(second_id)
    repository.mark_failed(
        analysis_id=second_id, reason_code="INTERNAL_ERROR"
    )
    mismatch = client.post(
        f"/v1/analyses/{second_id}/restart",
        headers=headers("analysis-restart-key-mismatch"),
        json=original,
    )
    assert mismatch.status_code == 422
    assert repository.get(second_id).status == AnalysisStatus.FAILED
