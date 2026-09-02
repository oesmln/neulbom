import pytest

from app.text.normalization import (
    compact_answer_text,
    normalize_answer_text,
    parse_korean_number,
)


def test_preserves_raw_transcript() -> None:
    raw_transcript = (
        "  민수는, 자전거를 탔어요!  "
    )

    normalized = normalize_answer_text(
        raw_transcript,
    )

    assert raw_transcript == (
        "  민수는, 자전거를 탔어요!  "
    )
    assert normalized == (
        "민수는 자전거를 탔어요"
    )


def test_normalizes_whitespace_and_punctuation() -> None:
    assert normalize_answer_text(
        "민수는,\t공원에   갔어요!"
    ) == "민수는 공원에 갔어요"


def test_normalizes_unicode_compatibility() -> None:
    assert normalize_answer_text(
        "ＡＢＣ ２０２６ 년"
    ) == "abc 2026년"


@pytest.mark.parametrize(
    ("raw_text", "expected"),
    [
        ("２０２６ 년", "2026년"),
        ("이천이십육 년", "2026년"),
        ("９ 월", "9월"),
        ("구 월", "9월"),
        ("３ 일", "3일"),
        ("삼 일", "3일"),
    ],
)
def test_normalizes_date_expressions(
    raw_text: str,
    expected: str,
) -> None:
    assert normalize_answer_text(
        raw_text,
    ) == expected


@pytest.mark.parametrize(
    "raw_text",
    [
        "11시",
        "11 시",
        "십일 시",
        "열한시",
        "열한 시",
    ],
)
def test_normalizes_equivalent_time_expressions(
    raw_text: str,
) -> None:
    assert normalize_answer_text(
        raw_text,
    ) == "11시"


@pytest.mark.parametrize(
    ("raw_text", "expected"),
    [
        ("월요일", "월요일"),
        ("월 요일", "월요일"),
        ("수 요 일", "수요일"),
        ("일 요일", "일요일"),
    ],
)
def test_normalizes_weekday_expressions(
    raw_text: str,
    expected: str,
) -> None:
    assert normalize_answer_text(
        raw_text,
    ) == expected


def test_compacts_normalized_answer() -> None:
    assert compact_answer_text(
        "산, 강, 수, 금"
    ) == "산강수금"

    assert compact_answer_text(
        "열한 시"
    ) == "11시"


@pytest.mark.parametrize(
    ("raw_text", "expected"),
    [
        ("육", 6),
        ("구", 9),
        ("칠", 7),
        ("삼", 3),
        ("십일", 11),
        ("열한", 11),
        ("이천이십육", 2026),
        ("이공이육", 2026),
        ("２０２６", 2026),
    ],
)
def test_parses_korean_numbers(
    raw_text: str,
    expected: int,
) -> None:
    assert parse_korean_number(
        raw_text,
    ) == expected


@pytest.mark.parametrize(
    "raw_text",
    [
        "",
        "민수",
        "열한시부터",
    ],
)
def test_returns_none_for_non_number(
    raw_text: str,
) -> None:
    assert parse_korean_number(
        raw_text,
    ) is None


def test_rejects_non_string_input() -> None:
    with pytest.raises(
        TypeError,
        match="문자열",
    ):
        normalize_answer_text(123)  # type: ignore[arg-type]

    with pytest.raises(
        TypeError,
        match="문자열",
    ):
        parse_korean_number(123)  # type: ignore[arg-type]