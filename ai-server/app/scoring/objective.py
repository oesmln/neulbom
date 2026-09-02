import re
from dataclasses import dataclass
from datetime import date
from typing import Any, Literal

from app.contracts.models import ContractBundle
from app.text.normalization import (
    compact_answer_text,
    normalize_answer_text,
    parse_korean_number,
)

AnswerStatus = Literal[
    "correct",
    "incorrect",
]

WrongEventReason = Literal[
    "OBJECTIVE_INCORRECT",
]

_DATE_QUESTION_UNITS = {
    "orientation_year": "년",
    "orientation_month": "월",
    "orientation_day": "일",
}

_WEEKDAY_BY_INDEX = (
    "월요일",
    "화요일",
    "수요일",
    "목요일",
    "금요일",
    "토요일",
    "일요일",
)

_POLITE_ENDING_PATTERN = re.compile(
    r"(?:입니다|이에요|예요|이요|요)$",
)

_KOREAN_SEQUENCE_PATTERN = re.compile(
    r"(?<![가-힣])"
    r"(?:[공영일이삼사오육칠팔구십백천만]+)"
    r"(?![가-힣])",
)

_ARABIC_SEQUENCE_PATTERN = re.compile(
    r"\d+",
)


@dataclass(frozen=True, slots=True)
class ObjectiveScore:
    question_code: str
    normalized_transcript: str
    scoring_status: Literal["scored"]
    answer_status: AnswerStatus
    wrong_event: Literal[0, 1]
    wrong_event_reason: (
        WrongEventReason | None
    )


@dataclass(frozen=True, slots=True)
class _ObjectiveQuestionRule:
    question_code: str
    answer_spec: dict[str, Any]


class ObjectiveScoringService:
    def __init__(
        self,
        *,
        rules: dict[
            str,
            _ObjectiveQuestionRule,
        ],
    ) -> None:
        if not rules:
            raise ValueError(
                "객관 문항 규칙이 하나 이상 필요합니다.",
            )

        self._rules = rules

    @classmethod
    def from_contract_bundle(
        cls,
        bundle: ContractBundle,
    ) -> "ObjectiveScoringService":
        policy = (
            bundle
            .wrong_event
            .operational_runtime_contract
            .objective_answer_policy
        )
        policy_extra = policy.model_extra or {}

        if (
            policy_extra.get(
                "exact_sequence_requires_order",
            )
            is not True
        ):
            raise ValueError(
                "객관 문항 순서 판정 정책이 "
                "계약과 일치하지 않습니다.",
            )

        if (
            policy_extra.get(
                "exact_sequence_allows_extra_units",
            )
            is not False
        ):
            raise ValueError(
                "추가 응답 단위 허용 정책이 "
                "계약과 일치하지 않습니다.",
            )

        objective_codes = set(
            policy.applies_to_question_codes,
        )
        rules: dict[
            str,
            _ObjectiveQuestionRule,
        ] = {}

        for question in bundle.cist.questions:
            if (
                question.question_code
                not in objective_codes
            ):
                continue

            question_extra = (
                question.model_extra or {}
            )
            answer_spec = question_extra.get(
                "answer_spec",
            )

            if not isinstance(
                answer_spec,
                dict,
            ):
                raise ValueError(
                    "객관 문항 answer_spec이 "
                    "누락되었습니다: "
                    f"{question.question_code}",
                )

            rules[question.question_code] = (
                _ObjectiveQuestionRule(
                    question_code=(
                        question.question_code
                    ),
                    answer_spec=dict(
                        answer_spec,
                    ),
                )
            )

        if set(rules) != objective_codes:
            missing = sorted(
                objective_codes - set(rules),
            )
            raise ValueError(
                "객관 문항 규칙이 누락되었습니다: "
                f"{missing}",
            )

        return cls(rules=rules)

    def score(
        self,
        *,
        question_code: str,
        raw_transcript: str,
        assessment_local_date: date,
    ) -> ObjectiveScore:
        rule = self._rules.get(
            question_code,
        )

        if rule is None:
            raise ValueError(
                "자동 정오 판정 대상이 아닌 "
                f"문항입니다: {question_code}",
            )

        normalized = normalize_answer_text(
            raw_transcript,
        )

        if not compact_answer_text(
            normalized,
        ):
            raise ValueError(
                "판정 가능한 전사문이 필요합니다.",
            )

        correct = self._is_correct(
            rule=rule,
            normalized_transcript=normalized,
            assessment_local_date=(
                assessment_local_date
            ),
        )

        if correct:
            return ObjectiveScore(
                question_code=question_code,
                normalized_transcript=normalized,
                scoring_status="scored",
                answer_status="correct",
                wrong_event=0,
                wrong_event_reason=None,
            )

        return ObjectiveScore(
            question_code=question_code,
            normalized_transcript=normalized,
            scoring_status="scored",
            answer_status="incorrect",
            wrong_event=1,
            wrong_event_reason=(
                "OBJECTIVE_INCORRECT"
            ),
        )

    def _is_correct(
        self,
        *,
        rule: _ObjectiveQuestionRule,
        normalized_transcript: str,
        assessment_local_date: date,
    ) -> bool:
        question_code = rule.question_code
        answer_spec = rule.answer_spec
        answer_type = answer_spec.get(
            "answer_type",
        )

        if question_code in _DATE_QUESTION_UNITS:
            expected_value = _expected_date_value(
                question_code=question_code,
                assessment_local_date=(
                    assessment_local_date
                ),
            )
            return _matches_temporal_value(
                normalized_transcript,
                expected_value=expected_value,
                unit=_DATE_QUESTION_UNITS[
                    question_code
                ],
            )

        if question_code == "orientation_weekday":
            return _matches_weekday(
                normalized_transcript,
                expected_weekday=(
                    _WEEKDAY_BY_INDEX[
                        assessment_local_date.weekday()
                    ]
                ),
            )

        if answer_type == "exact_sequence":
            expected_units = answer_spec.get(
                "expected_units",
            )

            if not isinstance(
                expected_units,
                list,
            ):
                raise ValueError(
                    "exact_sequence의 expected_units가 "
                    "누락되었습니다.",
                )

            return _matches_exact_sequence(
                normalized_transcript,
                expected_units=[
                    str(unit)
                    for unit in expected_units
                ],
            )

        if (
            answer_type
            == "multiple_choice_recognition"
        ):
            expected_answer = answer_spec.get(
                "expected_answer",
            )
            options = answer_spec.get(
                "options",
            )

            if (
                not isinstance(
                    expected_answer,
                    str,
                )
                or not isinstance(options, list)
            ):
                raise ValueError(
                    "재인 문항 정답 또는 선택지가 "
                    "누락되었습니다.",
                )

            return _matches_recognition_answer(
                normalized_transcript,
                expected_answer=expected_answer,
                options=[
                    str(option)
                    for option in options
                ],
            )

        raise ValueError(
            "지원하지 않는 answer_type입니다: "
            f"{answer_type}",
        )


def _expected_date_value(
    *,
    question_code: str,
    assessment_local_date: date,
) -> int:
    if question_code == "orientation_year":
        return assessment_local_date.year

    if question_code == "orientation_month":
        return assessment_local_date.month

    if question_code == "orientation_day":
        return assessment_local_date.day

    raise ValueError(
        f"지원하지 않는 날짜 문항입니다: {question_code}",
    )


def _matches_temporal_value(
    normalized_transcript: str,
    *,
    expected_value: int,
    unit: str,
) -> bool:
    explicit_pattern = re.compile(
        rf"(?<!\d)(\d+){re.escape(unit)}",
    )
    explicit_values = {
        int(value)
        for value in explicit_pattern.findall(
            normalized_transcript,
        )
    }

    if explicit_values:
        return explicit_values == {
            expected_value,
        }

    stripped = _strip_polite_ending(
        normalized_transcript,
    )
    compact = stripped.replace(" ", "")

    candidates: set[int] = set()

    parsed = parse_korean_number(compact)
    if parsed is not None:
        candidates.add(parsed)

    # '삼일'은 숫자 31 또는 3일로 해석될 수 있다.
    # 날짜 문항에서는 마지막 '일'을 날짜 단위로 본
    # 후보도 함께 계산한다.
    if (
        unit == "일"
        and compact.endswith("일")
        and len(compact) > 1
    ):
        day_value = parse_korean_number(
            compact[:-1],
        )

        if day_value is not None:
            candidates.add(day_value)

    return expected_value in candidates


def _matches_weekday(
    normalized_transcript: str,
    *,
    expected_weekday: str,
) -> bool:
    observed_weekdays = {
        weekday
        for weekday in _WEEKDAY_BY_INDEX
        if weekday in normalized_transcript
    }

    return observed_weekdays == {
        expected_weekday,
    }


def _matches_exact_sequence(
    normalized_transcript: str,
    *,
    expected_units: list[str],
) -> bool:
    if all(
        unit.isdecimal()
        for unit in expected_units
    ):
        observed_units = (
            _extract_numeric_sequence(
                normalized_transcript,
            )
        )
        return observed_units == tuple(
            expected_units,
        )

    stripped = _strip_polite_ending(
        normalized_transcript,
    )
    observed = compact_answer_text(
        stripped,
    )
    expected = "".join(
        compact_answer_text(unit)
        for unit in expected_units
    )

    return observed == expected


def _extract_numeric_sequence(
    normalized_transcript: str,
) -> tuple[str, ...]:
    stripped = _strip_polite_ending(
        normalized_transcript,
    )

    chunks: list[
        tuple[int, str]
    ] = []

    for match in (
        _ARABIC_SEQUENCE_PATTERN.finditer(
            stripped,
        )
    ):
        chunks.append(
            (
                match.start(),
                match.group(0),
            ),
        )

    for match in (
        _KOREAN_SEQUENCE_PATTERN.finditer(
            stripped,
        )
    ):
        parsed = parse_korean_number(
            match.group(0),
        )

        if parsed is None:
            continue

        chunks.append(
            (
                match.start(),
                str(parsed),
            ),
        )

    chunks.sort(
        key=lambda item: item[0],
    )

    return tuple(
        digit
        for _, chunk in chunks
        for digit in chunk
    )


def _matches_recognition_answer(
    normalized_transcript: str,
    *,
    expected_answer: str,
    options: list[str],
) -> bool:
    stripped = _strip_polite_ending(
        normalized_transcript,
    )
    compact = compact_answer_text(
        stripped,
    )
    normalized_expected = compact_answer_text(
        expected_answer,
    )

    matched_options = {
        compact_answer_text(option)
        for option in options
        if compact_answer_text(option) in compact
    }

    return matched_options == {
        normalized_expected,
    }


def _strip_polite_ending(
    text: str,
) -> str:
    return _POLITE_ENDING_PATTERN.sub(
        "",
        text,
    ).strip()