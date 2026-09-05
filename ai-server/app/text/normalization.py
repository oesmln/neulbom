import re
import unicodedata

_WHITESPACE_PATTERN = re.compile(r"\s+")

_WEEKDAY_PATTERN = re.compile(
    r"([월화수목금토일])\s*요\s*일",
)

_ARABIC_TEMPORAL_PATTERN = re.compile(
    r"(?P<number>\d+)\s*(?P<unit>년|월|일|시)",
)

_KOREAN_NUMBER_CHARACTERS = (
    "공영일이삼사오육칠팔구"
    "십백천만"
    "한두세네"
    "다섯여섯일곱여덟아홉"
    "열"
)

_KOREAN_TEMPORAL_PATTERN = re.compile(
    rf"(?P<number>"
    rf"[{_KOREAN_NUMBER_CHARACTERS}]+"
    rf"(?:\s+[{_KOREAN_NUMBER_CHARACTERS}]+)*"
    rf")\s*(?P<unit>년|월|시)",
)

# '일'은 숫자 1과 날짜 단위가 동일하므로,
# 한국어 숫자와 날짜 단위 사이에 공백이 있을 때만 변환한다.
_KOREAN_DAY_PATTERN = re.compile(
    rf"(?P<number>"
    rf"[{_KOREAN_NUMBER_CHARACTERS}]+"
    rf"(?:\s+[{_KOREAN_NUMBER_CHARACTERS}]+)*"
    rf")\s+일",
)

_SINO_DIGITS = {
    "공": 0,
    "영": 0,
    "일": 1,
    "이": 2,
    "삼": 3,
    "사": 4,
    "오": 5,
    "육": 6,
    "칠": 7,
    "팔": 8,
    "구": 9,
}

_SMALL_UNITS = {
    "십": 10,
    "백": 100,
    "천": 1_000,
}

_NATIVE_NUMBERS = {
    "하나": 1,
    "한": 1,
    "둘": 2,
    "두": 2,
    "셋": 3,
    "세": 3,
    "넷": 4,
    "네": 4,
    "다섯": 5,
    "여섯": 6,
    "일곱": 7,
    "여덟": 8,
    "아홉": 9,
    "열": 10,
    "열한": 11,
    "열두": 12,
    "열세": 13,
    "열네": 14,
    "열다섯": 15,
    "열여섯": 16,
    "열일곱": 17,
    "열여덟": 18,
    "열아홉": 19,
    "스물": 20,
}


def normalize_answer_text(text: str) -> str:
    """
    원본 전사문을 변경하지 않고 판정용 사본을 정규화한다.

    처리 내용:
    - Unicode NFKC 정규화
    - 영문 소문자화
    - 문장부호와 기호를 공백으로 변환
    - 연속 공백 제거
    - 요일 표현 통일
    - 명확한 연·월·일·시 숫자 표현 통일
    """

    if not isinstance(text, str):
        raise TypeError(
            "text는 문자열이어야 합니다.",
        )

    normalized = unicodedata.normalize(
        "NFKC",
        text,
    ).casefold()

    normalized = "".join(
        (
            " "
            if _is_separator(character)
            else character
        )
        for character in normalized
    )
    normalized = _collapse_whitespace(normalized)

    normalized = _WEEKDAY_PATTERN.sub(
        lambda match: (
            f"{match.group(1)}요일"
        ),
        normalized,
    )

    normalized = _KOREAN_DAY_PATTERN.sub(
        _replace_korean_day,
        normalized,
    )
    normalized = _KOREAN_TEMPORAL_PATTERN.sub(
        _replace_korean_temporal_expression,
        normalized,
    )
    normalized = _ARABIC_TEMPORAL_PATTERN.sub(
        lambda match: (
            f"{int(match.group('number'))}"
            f"{match.group('unit')}"
        ),
        normalized,
    )

    return _collapse_whitespace(normalized)


def compact_answer_text(text: str) -> str:
    """
    단어 포함 여부를 비교할 수 있도록 공백까지 제거한다.

    예:
    - '열한 시' → '11시'
    - '산 강 수 금' → '산강수금'
    """

    normalized = normalize_answer_text(text)
    return normalized.replace(" ", "")


def parse_korean_number(
    text: str,
) -> int | None:
    """
    아라비아 숫자 또는 한국어 숫자를 정수로 변환한다.

    문항의 의미는 판단하지 않는다. 연·월·일 등의 문맥 판정은
    이후 객관식 정오 판정 단계에서 수행한다.
    """

    if not isinstance(text, str):
        raise TypeError(
            "text는 문자열이어야 합니다.",
        )

    value = unicodedata.normalize(
        "NFKC",
        text,
    )
    value = _WHITESPACE_PATTERN.sub("", value)

    if not value:
        return None

    if value.isdecimal():
        return int(value)

    native_value = _NATIVE_NUMBERS.get(value)
    if native_value is not None:
        return native_value

    allowed_characters = (
        set(_SINO_DIGITS)
        | set(_SMALL_UNITS)
        | {"만"}
    )

    if any(
        character not in allowed_characters
        for character in value
    ):
        return None

    has_number_unit = any(
        character in _SMALL_UNITS
        or character == "만"
        for character in value
    )

    if not has_number_unit:
        digits = "".join(
            str(_SINO_DIGITS[character])
            for character in value
        )
        return int(digits)

    total = 0
    section = 0
    current_digit = 0

    for character in value:
        if character in _SINO_DIGITS:
            current_digit = _SINO_DIGITS[
                character
            ]
            continue

        if character in _SMALL_UNITS:
            multiplier = _SMALL_UNITS[
                character
            ]
            section += (
                current_digit
                if current_digit != 0
                else 1
            ) * multiplier
            current_digit = 0
            continue

        if character == "만":
            section += current_digit
            total += (
                section
                if section != 0
                else 1
            ) * 10_000
            section = 0
            current_digit = 0

    return total + section + current_digit


def _replace_korean_temporal_expression(
    match: re.Match[str],
) -> str:
    number = parse_korean_number(
        match.group("number"),
    )

    if number is None:
        return match.group(0)

    return f"{number}{match.group('unit')}"


def _replace_korean_day(
    match: re.Match[str],
) -> str:
    number = parse_korean_number(
        match.group("number"),
    )

    if number is None:
        return match.group(0)

    return f"{number}일"


def _is_separator(
    character: str,
) -> bool:
    if character == "_":
        return True

    category = unicodedata.category(
        character,
    )

    return category.startswith(
        ("P", "S"),
    )


def _collapse_whitespace(
    text: str,
) -> str:
    return _WHITESPACE_PATTERN.sub(
        " ",
        text,
    ).strip()