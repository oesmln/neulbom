import json
import shutil
from pathlib import Path

import pytest

from app.contracts.loader import (
    ContractLoadError,
    load_contract_bundle,
)
from app.contracts.validator import (
    ContractValidationError,
)
from app.core.config import PROJECT_ROOT

CONTRACT_FILE_NAMES = (
    "cist-v1.json",
    "wrong-event-v1.json",
)


@pytest.fixture
def contracts_dir(
    tmp_path: Path,
) -> Path:
    source_dir = PROJECT_ROOT / "contracts"

    for file_name in CONTRACT_FILE_NAMES:
        shutil.copy2(
            source_dir / file_name,
            tmp_path / file_name,
        )

    return tmp_path


def test_loads_current_contract_bundle(
    contracts_dir: Path,
) -> None:
    bundle = load_contract_bundle(contracts_dir)

    assert bundle.cist.question_set_version == "cist-v1"
    assert (
        bundle.wrong_event.wrong_event_rule_version
        == "wrong-event-v1"
    )
    assert len(bundle.cist.questions) == 17


def test_raises_when_contract_file_is_missing(
    contracts_dir: Path,
) -> None:
    (
        contracts_dir / "wrong-event-v1.json"
    ).unlink()

    with pytest.raises(
        ContractLoadError,
        match="찾을 수 없습니다",
    ):
        load_contract_bundle(contracts_dir)


def test_raises_when_json_is_invalid(
    contracts_dir: Path,
) -> None:
    (
        contracts_dir / "cist-v1.json"
    ).write_text(
        "{",
        encoding="utf-8",
    )

    with pytest.raises(
        ContractLoadError,
        match="JSON 문법",
    ):
        load_contract_bundle(contracts_dir)


def test_raises_when_contract_version_mismatches(
    contracts_dir: Path,
) -> None:
    cist_path = contracts_dir / "cist-v1.json"
    payload = _read_json(cist_path)
    payload["question_set_version"] = "cist-v2"
    _write_json(cist_path, payload)

    with pytest.raises(
        ContractValidationError,
        match="question_set_version",
    ):
        load_contract_bundle(contracts_dir)


def test_raises_when_question_is_missing(
    contracts_dir: Path,
) -> None:
    cist_path = contracts_dir / "cist-v1.json"
    payload = _read_json(cist_path)
    payload["questions"].pop()
    _write_json(cist_path, payload)

    with pytest.raises(
        ContractValidationError,
        match="정확히 17개",
    ):
        load_contract_bundle(contracts_dir)


def test_raises_when_question_is_duplicated(
    contracts_dir: Path,
) -> None:
    cist_path = contracts_dir / "cist-v1.json"
    payload = _read_json(cist_path)
    payload["questions"][-1] = payload["questions"][0]
    _write_json(cist_path, payload)

    with pytest.raises(
        ContractValidationError,
        match="legacy_question_id에 중복값",
    ):
        load_contract_bundle(contracts_dir)


def test_raises_on_unknown_wrong_event_question_code(
    contracts_dir: Path,
) -> None:
    wrong_event_path = (
        contracts_dir / "wrong-event-v1.json"
    )
    payload = _read_json(wrong_event_path)

    objective_policy = payload[
        "operational_runtime_contract"
    ]["objective_answer_policy"]

    objective_policy[
        "applies_to_question_codes"
    ].append("unknown_question")

    _write_json(
        wrong_event_path,
        payload,
    )

    with pytest.raises(
        ContractValidationError,
        match="존재하지 않는 question_code",
    ):
        load_contract_bundle(contracts_dir)


def _read_json(
    path: Path,
) -> dict:
    return json.loads(
        path.read_text(encoding="utf-8"),
    )


def _write_json(
    path: Path,
    payload: dict,
) -> None:
    path.write_text(
        json.dumps(
            payload,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )