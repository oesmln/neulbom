import argparse
import csv
import json
import platform
from collections import Counter
from dataclasses import asdict
from importlib.metadata import version
from pathlib import Path
from typing import Any

import numpy as np
import torch
from silero_vad import (
    get_speech_timestamps,
    load_silero_vad,
)

from app.audio.preprocessing import (
    TARGET_SAMPLE_RATE,
    preprocess_audio,
)
from app.audio.vad_calibration import (
    VadCandidate,
    VadObservation,
    choose_best_candidate,
    summarize_candidate,
)

PREPENDED_SILENCE_MS = 1_000
MAX_CALIBRATION_CLIP_SECONDS = 5.0

CANDIDATES = (
    VadCandidate(
        threshold=0.35,
        min_speech_duration_ms=100,
    ),
    VadCandidate(
        threshold=0.35,
        min_speech_duration_ms=250,
    ),
    VadCandidate(
        threshold=0.50,
        min_speech_duration_ms=100,
    ),
    VadCandidate(
        threshold=0.50,
        min_speech_duration_ms=250,
    ),
    VadCandidate(
        threshold=0.65,
        min_speech_duration_ms=100,
    ),
    VadCandidate(
        threshold=0.65,
        min_speech_duration_ms=250,
    ),
)


def main() -> None:
    args = _parse_args()

    samples, sample_metadata = _load_samples(
        clips_dir=args.clips_dir,
        metadata_csv=args.metadata_csv,
        stt_results_csv=args.stt_results_csv,
    )

    print(
        f"검증 음성 {len(samples)}개를 불러왔습니다.",
        flush=True,
    )

    torch.set_num_threads(1)
    model = load_silero_vad(
        onnx=True,
        opset_version=16,
    )

    summaries: list[dict[str, Any]] = []

    for candidate in CANDIDATES:
        print(
            f"후보 검증 시작: {candidate.identifier}",
            flush=True,
        )

        observations = _evaluate_candidate(
            model=model,
            candidate=candidate,
            samples=samples,
        )
        summary = summarize_candidate(
            candidate=candidate,
            observations=observations,
        )
        summaries.append(summary)

        print(
            (
                f"  detection="
                f"{summary['detection_rate']:.3f}, "
                f"false_early="
                f"{summary['false_early_rate']:.3f}, "
                f"median_abs="
                f"{summary['median_absolute_error_ms']}, "
                f"p95_abs="
                f"{summary['p95_absolute_error_ms']}, "
                f"passed={summary['passed']}"
            ),
            flush=True,
        )

    best = choose_best_candidate(summaries)

    report = {
        "schema_version": "vad-calibration-report-v1",
        "purpose": (
            "manual answer-onset aligned clips에 "
            "합성 무음을 추가하여 운영 VAD의 "
            "첫 발화 검출 오차를 검증"
        ),
        "method": {
            "model": "silero-vad",
            "model_release": "v6.2",
            "runtime": "onnx-cpu",
            "sample_rate": TARGET_SAMPLE_RATE,
            "prepended_silence_ms": (
                PREPENDED_SILENCE_MS
            ),
            "speech_pad_ms": 0,
            "maximum_clip_seconds_used": (
                MAX_CALIBRATION_CLIP_SECONDS
            ),
            "expected_onset_definition": (
                "수동 답변 시작점에 맞춘 클립 앞에 "
                "추가한 합성 무음의 종료 시각"
            ),
        },
        "selection_criteria": {
            "minimum_detection_rate": 0.98,
            "maximum_false_early_rate": 0.01,
            "maximum_median_absolute_error_ms": 150,
            "maximum_p95_absolute_error_ms": 300,
            "false_early_tolerance_ms": 100,
        },
        "dataset": sample_metadata,
        "runtime_versions": {
            "python": platform.python_version(),
            "numpy": version("numpy"),
            "torch": version("torch"),
            "onnxruntime": version("onnxruntime"),
            "silero-vad": version("silero-vad"),
        },
        "candidates": [
            asdict(candidate)
            for candidate in CANDIDATES
        ],
        "results": summaries,
        "recommended_candidate": {
            key: value
            for key, value in best.items()
            if key not in {
                "missed_examples",
                "worst_examples",
            }
        },
        "any_candidate_passed": any(
            summary["passed"]
            for summary in summaries
        ),
    }

    args.output.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    args.output.write_text(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    print(
        f"검증 결과 저장: {args.output.resolve()}",
        flush=True,
    )
    print(
        (
            "추천 후보: "
            f"{best['candidate_id']} "
            f"(passed={best['passed']})"
        ),
        flush=True,
    )


def _evaluate_candidate(
    *,
    model,
    candidate: VadCandidate,
    samples: list[tuple[str, np.ndarray]],
) -> list[VadObservation]:
    observations: list[VadObservation] = []

    for index, (
        filename,
        waveform,
    ) in enumerate(
        samples,
        start=1,
    ):
        timestamps = get_speech_timestamps(
            torch.from_numpy(waveform),
            model,
            threshold=candidate.threshold,
            sampling_rate=TARGET_SAMPLE_RATE,
            min_speech_duration_ms=(
                candidate.min_speech_duration_ms
            ),
            min_silence_duration_ms=(
                candidate.min_silence_duration_ms
            ),
            speech_pad_ms=candidate.speech_pad_ms,
            return_seconds=False,
        )

        if timestamps:
            first_start_sample = int(
                timestamps[0]["start"],
            )
            detected_onset_ms = round(
                first_start_sample
                / TARGET_SAMPLE_RATE
                * 1000,
            )
        else:
            detected_onset_ms = None

        observations.append(
            VadObservation(
                filename=filename,
                expected_onset_ms=(
                    PREPENDED_SILENCE_MS
                ),
                detected_onset_ms=detected_onset_ms,
            ),
        )

        if index % 50 == 0:
            print(
                f"  {index}/{len(samples)} 처리",
                flush=True,
            )

    return observations


def _load_samples(
    *,
    clips_dir: Path,
    metadata_csv: Path,
    stt_results_csv: Path,
) -> tuple[
    list[tuple[str, np.ndarray]],
    dict[str, Any],
]:
    metadata_rows = _read_csv(metadata_csv)
    stt_rows = _read_csv(stt_results_csv)

    metadata_by_filename = {
        row["파일명"]: row
        for row in metadata_rows
    }

    eligible_rows = [
        row
        for row in stt_rows
        if (
            row.get("status") == "success"
            and row.get("question_id", "").strip()
        )
    ]

    silence_samples = round(
        TARGET_SAMPLE_RATE
        * PREPENDED_SILENCE_MS
        / 1000,
    )
    maximum_clip_samples = round(
        TARGET_SAMPLE_RATE
        * MAX_CALIBRATION_CLIP_SECONDS,
    )

    samples: list[tuple[str, np.ndarray]] = []
    category_counts: Counter[str] = Counter()
    overlap_counts: Counter[str] = Counter()
    manual_delays: list[float] = []
    missing_files: list[str] = []

    for stt_row in eligible_rows:
        filename = stt_row["파일명"]
        metadata = metadata_by_filename.get(
            filename,
        )

        if metadata is None:
            raise RuntimeError(
                f"클립 메타데이터 누락: {filename}",
            )

        subject_id = metadata["대상자ID"]
        audio_path = (
            clips_dir
            / subject_id
            / f"{filename}.wav"
        )

        if not audio_path.is_file():
            missing_files.append(
                str(audio_path),
            )
            continue

        processed = preprocess_audio(
            content=audio_path.read_bytes(),
            content_type="audio/wav",
            max_duration_seconds=70.0,
        )

        answer_waveform = processed.waveform[
            :maximum_clip_samples
        ]
        waveform = np.concatenate(
            (
                np.zeros(
                    silence_samples,
                    dtype=np.float32,
                ),
                answer_waveform,
            ),
        ).astype(
            np.float32,
            copy=False,
        )

        samples.append(
            (
                filename,
                np.ascontiguousarray(waveform),
            ),
        )

        category_counts[
            metadata["질문범주"]
        ] += 1
        overlap_counts[
            metadata["겹침여부"]
        ] += 1
        manual_delays.append(
            float(metadata["응답지연 시간"]),
        )

    if missing_files:
        preview = "\n".join(
            missing_files[:10],
        )
        raise FileNotFoundError(
            "검증 음성 파일이 누락되었습니다:\n"
            f"{preview}",
        )

    if not samples:
        raise RuntimeError(
            "검증할 음성을 찾지 못했습니다.",
        )

    return samples, {
        "sample_count": len(samples),
        "selection": (
            "Google STT status=success and "
            "non-empty question_id"
        ),
        "category_counts": dict(
            sorted(category_counts.items()),
        ),
        "overlap_counts": dict(
            sorted(overlap_counts.items()),
        ),
        "manual_response_delay_seconds": {
            "minimum": round(
                min(manual_delays),
                6,
            ),
            "median": round(
                float(np.median(manual_delays)),
                6,
            ),
            "maximum": round(
                max(manual_delays),
                6,
            ),
        },
    }


def _read_csv(
    path: Path,
) -> list[dict[str, str]]:
    if not path.is_file():
        raise FileNotFoundError(
            f"CSV 파일을 찾을 수 없습니다: {path}",
        )

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as file:
        return list(
            csv.DictReader(file),
        )


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "수동 답변 시작점 기반 클립으로 "
            "Silero VAD 후보를 비교합니다."
        ),
    )
    parser.add_argument(
        "--clips-dir",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--metadata-csv",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--stt-results-csv",
        type=Path,
        required=True,
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(
            "validation/vad-calibration-v1.json",
        ),
    )

    return parser.parse_args()


if __name__ == "__main__":
    main()