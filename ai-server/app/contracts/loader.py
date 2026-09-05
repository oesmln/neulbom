import json
from json import JSONDecodeError
from pathlib import Path
from typing import TypeVar

from pydantic import BaseModel, ValidationError

from app.contracts.models import (
    CistQuestionSet,
    ContractBundle,
    WrongEventRuleSet,
)
from app.contracts.validator import validate_contract_bundle

CIST_CONTRACT_FILE = "cist-v1.json"
WRONG_EVENT_CONTRACT_FILE = "wrong-event-v1.json"

ContractModelType = TypeVar(
    "ContractModelType",
    bound=BaseModel,
)


class ContractLoadError(ValueError):
    """계약 파일을 읽거나 모델로 변환할 수 없을 때 발생한다."""


def load_contract_bundle(
    contracts_dir: Path,
) -> ContractBundle:
    """두 기준 계약 파일을 읽고 검증한다."""
    cist = _load_contract(
        contracts_dir / CIST_CONTRACT_FILE,
        CistQuestionSet,
    )
    wrong_event = _load_contract(
        contracts_dir / WRONG_EVENT_CONTRACT_FILE,
        WrongEventRuleSet,
    )

    bundle = ContractBundle(
        cist=cist,
        wrong_event=wrong_event,
    )
    validate_contract_bundle(bundle)

    return bundle


def _load_contract(
    path: Path,
    model_type: type[ContractModelType],
) -> ContractModelType:
    try:
        raw_data = json.loads(
            path.read_text(encoding="utf-8"),
        )
    except FileNotFoundError as error:
        raise ContractLoadError(
            f"계약 파일을 찾을 수 없습니다: {path}",
        ) from error
    except UnicodeDecodeError as error:
        raise ContractLoadError(
            f"계약 파일은 UTF-8이어야 합니다: {path}",
        ) from error
    except JSONDecodeError as error:
        raise ContractLoadError(
            "계약 파일의 JSON 문법이 잘못되었습니다: "
            f"{path} "
            f"(line={error.lineno}, column={error.colno})",
        ) from error
    except OSError as error:
        raise ContractLoadError(
            f"계약 파일을 읽을 수 없습니다: {path}",
        ) from error

    try:
        return model_type.model_validate(raw_data)
    except ValidationError as error:
        raise ContractLoadError(
            f"계약 파일 구조가 올바르지 않습니다: {path}\n"
            f"{error}",
        ) from error