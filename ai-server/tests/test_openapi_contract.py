from dataclasses import dataclass
from pathlib import Path
from typing import Any, get_args

import pytest
import yaml

from app.api.schemas.analysis import (
    RetryAction,
)
from app.api.schemas.common import (
    AudioContentType,
    ConditionalQuestionCode,
    QuestionCode,
    RetryReasonCode,
)
from app.contracts.loader import (
    load_contract_bundle,
)
from app.core.config import (
    PROJECT_ROOT,
    Settings,
)
from app.main import create_app


@dataclass(frozen=True, slots=True)
class OperationExpectation:
    method: str
    operation_id: str
    success_status: str
    request_schema: str | None
    response_schemas: frozenset[str]


EXPECTED_OPERATIONS = {
    (
        "/v1/assessments/"
        "{assessment_id}/recognition-plan"
    ): OperationExpectation(
        method="post",
        operation_id="createRecognitionPlan",
        success_status="200",
        request_schema=(
            "RecognitionPlanRequest"
        ),
        response_schemas=frozenset(
            {
                (
                    "RecognitionPlan"
                    "CompletedResponse"
                ),
                (
                    "RecognitionPlan"
                    "NeedsRetryResponse"
                ),
            },
        ),
    ),
    "/v1/analyses": OperationExpectation(
        method="post",
        operation_id="createAnalysis",
        success_status="202",
        request_schema="AnalysisCreateRequest",
        response_schemas=frozenset(
            {
                "AnalysisAcceptedResponse",
            },
        ),
    ),
    (
        "/v1/analyses/"
        "{analysis_id}"
    ): OperationExpectation(
        method="get",
        operation_id="getAnalysis",
        success_status="200",
        request_schema=None,
        response_schemas=frozenset(
            {
                "AnalysisStatusResponse",
            },
        ),
    ),
    (
        "/v1/analyses/"
        "{analysis_id}/retry"
    ): OperationExpectation(
        method="post",
        operation_id="retryAnalysis",
        success_status="202",
        request_schema="AnalysisRetryRequest",
        response_schemas=frozenset(
            {
                "AnalysisAcceptedResponse",
            },
        ),
    ),
}


@pytest.fixture(scope="module")
def reference_openapi() -> dict[str, Any]:
    path = (
        PROJECT_ROOT
        / "contracts"
        / "ai-server-openapi-v1.yaml"
    )
    document = yaml.safe_load(
        path.read_text(
            encoding="utf-8",
        ),
    )

    assert isinstance(document, dict)

    return document


@pytest.fixture(scope="module")
def runtime_openapi() -> dict[str, Any]:
    return create_app().openapi()


def test_all_reference_refs_resolve(
    reference_openapi: dict[str, Any],
) -> None:
    references = set(
        _collect_references(
            reference_openapi,
        ),
    )

    assert references

    for reference in references:
        assert reference.startswith("#/")
        resolved = _resolve_reference(
            reference_openapi,
            reference,
        )
        assert resolved is not None


def test_runtime_operations_match_contract(
    reference_openapi: dict[str, Any],
    runtime_openapi: dict[str, Any],
) -> None:
    for (
        path,
        expectation,
    ) in EXPECTED_OPERATIONS.items():
        reference_operation = (
            reference_openapi["paths"][
                path
            ][expectation.method]
        )
        runtime_operation = (
            runtime_openapi["paths"][
                path
            ][expectation.method]
        )

        assert (
            reference_operation[
                "operationId"
            ]
            == expectation.operation_id
        )
        assert (
            runtime_operation[
                "operationId"
            ]
            == expectation.operation_id
        )

        assert (
            expectation.success_status
            in reference_operation[
                "responses"
            ]
        )
        assert (
            expectation.success_status
            in runtime_operation[
                "responses"
            ]
        )

        reference_request_names = (
            _request_schema_names(
                reference_operation,
            )
        )
        runtime_request_names = (
            _request_schema_names(
                runtime_operation,
            )
        )

        if expectation.request_schema is None:
            assert not reference_request_names
            assert not runtime_request_names
        else:
            assert expectation.request_schema in (
                reference_request_names
            )
            assert expectation.request_schema in (
                runtime_request_names
            )

        reference_response_names = (
            _response_schema_names(
                reference_operation,
                expectation.success_status,
            )
        )
        runtime_response_names = (
            _response_schema_names(
                runtime_operation,
                expectation.success_status,
            )
        )

        assert (
            expectation.response_schemas
            <= reference_response_names
        )
        assert (
            expectation.response_schemas
            <= runtime_response_names
        )


def test_runtime_operations_require_bearer_auth(
    reference_openapi: dict[str, Any],
    runtime_openapi: dict[str, Any],
) -> None:
    assert reference_openapi["security"] == [
        {
            "bearerAuth": [],
        },
    ]

    reference_scheme = (
        reference_openapi["components"][
            "securitySchemes"
        ]["bearerAuth"]
    )
    runtime_scheme = (
        runtime_openapi["components"][
            "securitySchemes"
        ]["bearerAuth"]
    )

    assert reference_scheme["type"] == "http"
    assert reference_scheme["scheme"] == (
        "bearer"
    )
    assert runtime_scheme["type"] == "http"
    assert runtime_scheme["scheme"] == (
        "bearer"
    )

    for (
        path,
        expectation,
    ) in EXPECTED_OPERATIONS.items():
        operation = runtime_openapi[
            "paths"
        ][path][expectation.method]

        assert {
            "bearerAuth": [],
        } in operation["security"]


def test_required_api_parameters_are_present(
    runtime_openapi: dict[str, Any],
) -> None:
    recognition_parameters = (
        _parameter_names(
            runtime_openapi["paths"][
                (
                    "/v1/assessments/"
                    "{assessment_id}/"
                    "recognition-plan"
                )
            ]["post"],
        )
    )
    create_parameters = _parameter_names(
        runtime_openapi["paths"][
            "/v1/analyses"
        ]["post"],
    )
    get_parameters = _parameter_names(
        runtime_openapi["paths"][
            "/v1/analyses/{analysis_id}"
        ]["get"],
    )
    retry_parameters = _parameter_names(
        runtime_openapi["paths"][
            (
                "/v1/analyses/"
                "{analysis_id}/retry"
            )
        ]["post"],
    )

    assert recognition_parameters == {
        "assessment_id",
        "Idempotency-Key",
    }
    assert create_parameters == {
        "Idempotency-Key",
    }
    assert get_parameters == {
        "analysis_id",
    }
    assert retry_parameters == {
        "analysis_id",
        "Idempotency-Key",
    }


def test_question_code_contracts_match(
    reference_openapi: dict[str, Any],
) -> None:
    bundle = load_contract_bundle(
        PROJECT_ROOT / "contracts",
    )

    contract_question_codes = {
        question.question_code
        for question
        in bundle.cist.questions
    }
    contract_conditional_codes = set(
        bundle
        .cist
        .administration
        .conditional_question_codes
    )

    python_question_codes = set(
        get_args(QuestionCode),
    )
    python_conditional_codes = set(
        get_args(ConditionalQuestionCode),
    )

    openapi_question_codes = set(
        reference_openapi["components"][
            "schemas"
        ]["QuestionCode"]["enum"],
    )
    openapi_conditional_codes = set(
        reference_openapi["components"][
            "schemas"
        ][
            "ConditionalQuestionCode"
        ]["enum"],
    )

    assert len(contract_question_codes) == 17
    assert (
        python_question_codes
        == contract_question_codes
        == openapi_question_codes
    )
    assert (
        python_conditional_codes
        == contract_conditional_codes
        == openapi_conditional_codes
    )


def test_retry_and_audio_enums_match(
    reference_openapi: dict[str, Any],
) -> None:
    schemas = reference_openapi[
        "components"
    ]["schemas"]

    assert set(
        schemas["RetryReasonCode"]["enum"],
    ) == set(
        get_args(RetryReasonCode),
    )
    assert set(
        schemas["RetryAction"]["enum"],
    ) == set(
        get_args(RetryAction),
    )
    assert set(
        schemas["AudioResource"][
            "properties"
        ]["content_type"]["enum"],
    ) == set(
        get_args(AudioContentType),
    )


def test_fixed_analysis_metadata_matches(
    reference_openapi: dict[str, Any],
    runtime_openapi: dict[str, Any],
) -> None:
    info = reference_openapi["info"]

    assert info[
        "x-question-set-version"
    ] == "cist-v1"
    assert info[
        "x-wrong-event-rule-version"
    ] == "wrong-event-v1"
    assert info[
        "x-default-threshold"
    ] == 0.5
    assert info[
        "x-threshold-version"
    ] == "fusion-threshold-v1"

    reference_result = (
        reference_openapi["components"][
            "schemas"
        ]["FinalAnalysisResult"]
    )
    runtime_result = (
        runtime_openapi["components"][
            "schemas"
        ]["FinalAnalysisResult"]
    )

    assert reference_result["properties"][
        "decision_threshold"
    ]["const"] == 0.5
    assert runtime_result["properties"][
        "decision_threshold"
    ]["const"] == 0.5

    assert reference_result["properties"][
        "threshold_version"
    ]["const"] == "fusion-threshold-v1"
    assert runtime_result["properties"][
        "threshold_version"
    ]["const"] == "fusion-threshold-v1"


def test_processing_timeout_matches_contract(
    reference_openapi: dict[str, Any],
) -> None:
    operation = reference_openapi[
        "paths"
    ]["/v1/analyses"]["post"]

    settings = Settings(
        _env_file=None,
    )
    configured_timeout_ms = int(
        settings
        .analysis_processing_timeout_seconds
        * 1000
    )

    assert operation[
        "x-processing-timeout-ms"
    ] == configured_timeout_ms
    assert configured_timeout_ms == 300_000


def _collect_references(
    value: Any,
):
    if isinstance(value, dict):
        reference = value.get("$ref")

        if isinstance(reference, str):
            yield reference

        for nested_value in value.values():
            yield from _collect_references(
                nested_value,
            )

    elif isinstance(value, list):
        for nested_value in value:
            yield from _collect_references(
                nested_value,
            )


def _resolve_reference(
    document: dict[str, Any],
    reference: str,
) -> Any:
    current: Any = document

    for raw_part in reference.removeprefix(
        "#/",
    ).split("/"):
        part = (
            raw_part
            .replace("~1", "/")
            .replace("~0", "~")
        )

        assert isinstance(current, dict)
        assert part in current

        current = current[part]

    return current


def _request_schema_names(
    operation: dict[str, Any],
) -> frozenset[str]:
    schema = (
        operation
        .get("requestBody", {})
        .get("content", {})
        .get("application/json", {})
        .get("schema", {})
    )

    return _schema_names(schema)


def _response_schema_names(
    operation: dict[str, Any],
    status_code: str,
) -> frozenset[str]:
    schema = (
        operation["responses"][
            status_code
        ]
        .get("content", {})
        .get("application/json", {})
        .get("schema", {})
    )

    return _schema_names(schema)


def _schema_names(
    schema: Any,
) -> frozenset[str]:
    return frozenset(
        reference.rsplit("/", 1)[-1]
        for reference
        in _collect_references(schema)
    )


def _parameter_names(
    operation: dict[str, Any],
) -> set[str]:
    return {
        parameter["name"]
        for parameter
        in operation.get("parameters", [])
        if (
            isinstance(parameter, dict)
            and "name" in parameter
        )
    }