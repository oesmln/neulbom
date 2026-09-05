from dataclasses import dataclass

from app.api.schemas.analysis import (
    AdministeredQuestionResponse,
    AnalysisCreateRequest,
    NotApplicableQuestionResponse,
)
from app.api.schemas.common import (
    ConditionalQuestionCode,
)
from app.contracts.models import ContractBundle


class AssessmentCompletenessError(
    ValueError,
):
    """세션 문항 구성이 계약과 일치하지 않는 경우."""


@dataclass(frozen=True, slots=True)
class AssessmentCompletenessResult:
    administered_responses: tuple[
        AdministeredQuestionResponse,
        ...,
    ]
    not_applicable_question_codes: tuple[
        ConditionalQuestionCode,
        ...,
    ]


class AssessmentCompletenessService:
    def __init__(
        self,
        *,
        question_order: tuple[str, ...],
        variant_by_question: dict[str, str],
        always_required_codes: set[str],
        conditional_codes: set[str],
        recognition_source_code: str,
        recognition_code_by_unit: dict[
            str,
            str,
        ],
    ) -> None:
        if len(question_order) != 17:
            raise ValueError(
                "CIST 문항은 정확히 "
                "17개여야 합니다.",
            )

        if len(set(question_order)) != 17:
            raise ValueError(
                "CIST 문항 코드가 중복되었습니다.",
            )

        expected_codes = set(question_order)

        if set(variant_by_question) != (
            expected_codes
        ):
            raise ValueError(
                "문항별 variant_id가 "
                "완전하지 않습니다.",
            )

        if (
            always_required_codes
            & conditional_codes
        ):
            raise ValueError(
                "필수 문항과 조건부 문항이 "
                "중복되었습니다.",
            )

        if (
            always_required_codes
            | conditional_codes
        ) != expected_codes:
            raise ValueError(
                "필수 문항과 조건부 문항의 합이 "
                "전체 CIST 문항과 일치하지 않습니다.",
            )

        if (
            recognition_source_code
            not in always_required_codes
        ):
            raise ValueError(
                "재인 계획 기준 문항은 필수 "
                "시행 문항이어야 합니다.",
            )

        if set(
            recognition_code_by_unit.values(),
        ) != conditional_codes:
            raise ValueError(
                "기억 단위별 재인 문항이 조건부 "
                "문항과 일치하지 않습니다.",
            )

        self._question_order = question_order
        self._variant_by_question = dict(
            variant_by_question,
        )
        self._always_required_codes = set(
            always_required_codes,
        )
        self._conditional_codes = set(
            conditional_codes,
        )
        self._recognition_source_code = (
            recognition_source_code
        )
        self._recognition_code_by_unit = dict(
            recognition_code_by_unit,
        )

    @classmethod
    def from_contract_bundle(
        cls,
        bundle: ContractBundle,
    ) -> "AssessmentCompletenessService":
        questions = bundle.cist.questions
        administration = (
            bundle.cist.administration
        )

        question_order = tuple(
            question.question_code
            for question in sorted(
                questions,
                key=lambda item: item.order,
            )
        )
        variant_by_question = {
            question.question_code: (
                question.variant_id
            )
            for question in questions
        }
        recognition_code_by_unit = {
            unit.unit_code: (
                unit.recognition_question_code
            )
            for unit in (
                bundle.cist.memory_story.units
            )
        }

        return cls(
            question_order=question_order,
            variant_by_question=(
                variant_by_question
            ),
            always_required_codes=set(
                administration
                .always_required_question_codes
            ),
            conditional_codes=set(
                administration
                .conditional_question_codes
            ),
            recognition_source_code=(
                administration
                .conditional_source_question_code
            ),
            recognition_code_by_unit=(
                recognition_code_by_unit
            ),
        )

    def validate(
        self,
        request: AnalysisCreateRequest,
    ) -> AssessmentCompletenessResult:
        responses_by_code: dict[
            str,
            AdministeredQuestionResponse
            | NotApplicableQuestionResponse,
        ] = {}

        duplicate_codes: set[str] = set()

        for response in request.responses:
            question_code = response.question_code

            if question_code in responses_by_code:
                duplicate_codes.add(
                    question_code,
                )

            responses_by_code[
                question_code
            ] = response

        if duplicate_codes:
            raise AssessmentCompletenessError(
                "문항 코드가 중복되었습니다: "
                f"{sorted(duplicate_codes)}",
            )

        expected_codes = set(
            self._question_order,
        )
        actual_codes = set(
            responses_by_code,
        )

        if actual_codes != expected_codes:
            missing_codes = sorted(
                expected_codes - actual_codes,
            )
            unexpected_codes = sorted(
                actual_codes - expected_codes,
            )

            raise AssessmentCompletenessError(
                "CIST 문항 코드 구성이 "
                "완전하지 않습니다: "
                f"missing={missing_codes}, "
                f"unexpected={unexpected_codes}",
            )

        self._validate_variants(
            responses_by_code,
        )
        selected_codes = (
            self._validate_recognition_plan(
                request,
            )
        )
        self._validate_administration_statuses(
            responses_by_code=responses_by_code,
            selected_codes=selected_codes,
        )

        administered_responses = tuple(
            response
            for question_code in self._question_order
            if isinstance(
                (
                    response
                    := responses_by_code[
                        question_code
                    ]
                ),
                AdministeredQuestionResponse,
            )
        )
        not_applicable_codes = tuple(
            question_code
            for question_code
            in self._question_order
            if isinstance(
                responses_by_code[
                    question_code
                ],
                NotApplicableQuestionResponse,
            )
        )

        self._validate_unique_identifiers(
            administered_responses,
        )

        return AssessmentCompletenessResult(
            administered_responses=(
                administered_responses
            ),
            not_applicable_question_codes=(
                not_applicable_codes
            ),
        )

    def _validate_variants(
        self,
        responses_by_code: dict[
            str,
            AdministeredQuestionResponse
            | NotApplicableQuestionResponse,
        ],
    ) -> None:
        mismatches: list[str] = []

        for (
            question_code,
            response,
        ) in responses_by_code.items():
            expected_variant = (
                self._variant_by_question[
                    question_code
                ]
            )

            if (
                response.variant_id
                != expected_variant
            ):
                mismatches.append(
                    question_code,
                )

        if mismatches:
            raise AssessmentCompletenessError(
                "variant_id가 기준 계약과 "
                "일치하지 않습니다: "
                f"{sorted(mismatches)}",
            )

    def _validate_recognition_plan(
        self,
        request: AnalysisCreateRequest,
    ) -> set[str]:
        plan = request.recognition_plan

        if (
            plan.source_question_code
            != self._recognition_source_code
        ):
            raise AssessmentCompletenessError(
                "recognition plan 기준 문항이 "
                "계약과 일치하지 않습니다.",
            )

        selected_codes = set(
            plan.selected_question_codes,
        )

        if not selected_codes.issubset(
            self._conditional_codes,
        ):
            raise AssessmentCompletenessError(
                "recognition plan에 조건부 문항이 "
                "아닌 코드가 포함되었습니다.",
            )

        recalled_units = (
            plan.recalled_units.model_dump()
        )
        expected_selected_codes = {
            self._recognition_code_by_unit[
                unit_code
            ]
            for unit_code, recalled
            in recalled_units.items()
            if not recalled
        }

        if (
            selected_codes
            != expected_selected_codes
        ):
            raise AssessmentCompletenessError(
                "selected_question_codes가 "
                "recalled_units의 false 값과 "
                "일치하지 않습니다.",
            )

        return selected_codes

    def _validate_administration_statuses(
        self,
        *,
        responses_by_code: dict[
            str,
            AdministeredQuestionResponse
            | NotApplicableQuestionResponse,
        ],
        selected_codes: set[str],
    ) -> None:
        invalid_required = sorted(
            question_code
            for question_code
            in self._always_required_codes
            if not isinstance(
                responses_by_code[
                    question_code
                ],
                AdministeredQuestionResponse,
            )
        )

        if invalid_required:
            raise AssessmentCompletenessError(
                "필수 문항은 모두 administered여야 "
                f"합니다: {invalid_required}",
            )

        invalid_conditional: list[str] = []

        for question_code in (
            self._conditional_codes
        ):
            response = responses_by_code[
                question_code
            ]

            should_be_administered = (
                question_code in selected_codes
            )
            is_administered = isinstance(
                response,
                AdministeredQuestionResponse,
            )

            if (
                should_be_administered
                != is_administered
            ):
                invalid_conditional.append(
                    question_code,
                )

        if invalid_conditional:
            raise AssessmentCompletenessError(
                "조건부 문항 시행 상태가 "
                "recognition plan과 "
                "일치하지 않습니다: "
                f"{sorted(invalid_conditional)}",
            )

    @staticmethod
    def _validate_unique_identifiers(
        responses: tuple[
            AdministeredQuestionResponse,
            ...,
        ],
    ) -> None:
        recording_ids = [
            response.recording_id
            for response in responses
        ]
        response_ids = [
            response.response_id
            for response in responses
        ]

        if len(recording_ids) != len(
            set(recording_ids),
        ):
            raise AssessmentCompletenessError(
                "recording_id가 중복되었습니다.",
            )

        if len(response_ids) != len(
            set(response_ids),
        ):
            raise AssessmentCompletenessError(
                "response_id가 중복되었습니다.",
            )