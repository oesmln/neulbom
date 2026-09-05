import argparse
import csv
import json
import platform
from collections import Counter
from dataclasses import asdict, dataclass
from importlib.metadata import version
from pathlib import Path
from typing import Any

import numpy as np
import torch
from silero_vad import get_speech_timestamps, load_silero_vad

from app.audio.preprocessing import TARGET_SAMPLE_RATE, preprocess_audio
from app.audio.vad_calibration import (
    VadAuditObservation,
    VadCandidate,
    VadObservation,
    choose_best_candidate,
    summarize_candidate,
)

PREPENDED_SILENCE_MS = 1_000
MAX_CALIBRATION_CLIP_SECONDS = 5.0

CANDIDATES = tuple(
    VadCandidate(
        threshold=threshold,
        min_speech_duration_ms=min_speech_duration_ms,
    )
    for threshold in (0.20, 0.25, 0.30, 0.35)
    for min_speech_duration_ms in (64, 100)
) + (
    VadCandidate(
        threshold=0.50,
        min_speech_duration_ms=100,
    ),
)


@dataclass(frozen=True, slots=True)
class CalibrationSample:
    filename: str
    waveform: np.ndarray
    subject_id: str = ""
    category: str = ""
    manual_response_delay_ms: int = 0


def main() -> None:
    args = _parse_args()
    positive_samples, empty_stt_samples, sample_metadata = _load_samples(
        clips_dir=args.clips_dir,
        metadata_csv=args.metadata_csv,
        stt_results_csv=args.stt_results_csv,
    )
    synthetic_nonspeech_samples = _build_synthetic_nonspeech_samples()

    print(
        "검증 자료를 불러왔습니다: "
        f"발화 양성 {len(positive_samples)}개, "
        f"빈 STT 감사 {len(empty_stt_samples)}개, "
        f"합성 비음성 {len(synthetic_nonspeech_samples)}개",
        flush=True,
    )

    torch.set_num_threads(1)
    model = load_silero_vad(onnx=True, opset_version=16)
    summaries: list[dict[str, Any]] = []

    for candidate in CANDIDATES:
        print(f"후보 검증 시작: {candidate.identifier}", flush=True)
        observations = _evaluate_candidate(
            model=model,
            candidate=candidate,
            samples=positive_samples,
        )
        empty_stt_audit = _evaluate_audit(
            model=model,
            candidate=candidate,
            samples=empty_stt_samples,
        )
        synthetic_nonspeech_audit = _evaluate_audit(
            model=model,
            candidate=candidate,
            samples=synthetic_nonspeech_samples,
        )
        summary = summarize_candidate(
            candidate=candidate,
            observations=observations,
            empty_stt_audit=empty_stt_audit,
            synthetic_nonspeech_audit=synthetic_nonspeech_audit,
        )
        summaries.append(summary)

        corrected = summary["corrected_error_metrics"]
        drift = summary["delay_feature_drift"]
        print(
            "  "
            f"detection={summary['detection_rate']:.3f}, "
            f"corrected_p95_ms={corrected['p95_absolute_error_ms']}, "
            f"fusion_p95_s={drift['p95_absolute_difference_seconds']}, "
            "synthetic_false_positive="
            f"{summary['synthetic_nonspeech_audit']['detected_count']}, "
            f"passed={summary['passed']}",
            flush=True,
        )

    best = choose_best_candidate(summaries)
    report = {
        "schema_version": "vad-calibration-report-v1",
        "purpose": (
            "수동 답변 시작점 기준 첫 발화 검출 오차, 비음성 오검출, "
            "최종 fusion 응답 지연 특성의 변화를 함께 검증"
        ),
        "method": {
            "model": "silero-vad",
            "model_release": version("silero-vad"),
            "runtime": "onnx-cpu",
            "sample_rate": TARGET_SAMPLE_RATE,
            "prepended_silence_ms": PREPENDED_SILENCE_MS,
            "speech_pad_ms": 0,
            "maximum_clip_seconds_used": MAX_CALIBRATION_CLIP_SECONDS,
            "expected_onset_definition": (
                "수동 답변 시작점에 맞춘 클립 앞에 추가한 "
                "합성 무음의 종료 시각"
            ),
            "bias_correction_definition": (
                "발화 양성 자료의 VAD 검출 오차 중앙값을 "
                "운영 첫 발화 시작 시각에서 차감"
            ),
            "delay_feature_definition": (
                "문항별 지연의 범주 중앙값을 구한 뒤 "
                "네 핵심 범주의 평균을 계산"
            ),
        },
        "selection_criteria": {
            "minimum_detection_rate": 0.99,
            "maximum_corrected_false_early_rate": 0.01,
            "maximum_corrected_median_absolute_error_ms": 100,
            "maximum_corrected_p95_absolute_error_ms": 300,
            "maximum_synthetic_nonspeech_detection_rate": 0.0,
            "maximum_fusion_delay_p95_absolute_difference_seconds": 0.15,
            "false_early_tolerance_ms": 100,
            "empty_stt_audit_policy": (
                "빈 STT는 실제 무음 정답 자료가 아니므로 "
                "후보 탈락 조건이 아닌 참고 지표로만 사용"
            ),
        },
        "dataset": sample_metadata,
        "runtime_versions": {
            "python": platform.python_version(),
            "numpy": version("numpy"),
            "torch": version("torch"),
            "onnxruntime": version("onnxruntime"),
            "silero-vad": version("silero-vad"),
        },
        "candidates": [asdict(candidate) for candidate in CANDIDATES],
        "synthetic_nonspeech_cases": [
            sample.filename for sample in synthetic_nonspeech_samples
        ],
        "results": summaries,
        "recommended_candidate": {
            key: value
            for key, value in best.items()
            if key not in {"missed_examples", "worst_examples"}
        },
        "any_candidate_passed": any(
            summary["passed"] for summary in summaries
        ),
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"검증 결과 저장: {args.output.resolve()}", flush=True)
    print(
        f"추천 후보: {best['candidate_id']} "
        f"(passed={best['passed']})",
        flush=True,
    )


def _evaluate_candidate(
    *,
    model,
    candidate: VadCandidate,
    samples: list[CalibrationSample],
) -> list[VadObservation]:
    observations: list[VadObservation] = []
    for index, sample in enumerate(samples, start=1):
        detected_onset_ms = _first_speech_onset_ms(
            model=model,
            candidate=candidate,
            waveform=sample.waveform,
        )
        observations.append(
            VadObservation(
                filename=sample.filename,
                expected_onset_ms=PREPENDED_SILENCE_MS,
                detected_onset_ms=detected_onset_ms,
                subject_id=sample.subject_id,
                category=sample.category,
                manual_response_delay_ms=(
                    sample.manual_response_delay_ms
                ),
            ),
        )
        if index % 50 == 0:
            print(f"  발화 양성 {index}/{len(samples)} 처리", flush=True)
    return observations


def _evaluate_audit(
    *,
    model,
    candidate: VadCandidate,
    samples: list[CalibrationSample],
) -> list[VadAuditObservation]:
    return [
        VadAuditObservation(
            filename=sample.filename,
            detected=(
                _first_speech_onset_ms(
                    model=model,
                    candidate=candidate,
                    waveform=sample.waveform,
                )
                is not None
            ),
        )
        for sample in samples
    ]


def _first_speech_onset_ms(
    *,
    model,
    candidate: VadCandidate,
    waveform: np.ndarray,
) -> int | None:
    timestamps = get_speech_timestamps(
        torch.from_numpy(waveform),
        model,
        threshold=candidate.threshold,
        sampling_rate=TARGET_SAMPLE_RATE,
        min_speech_duration_ms=candidate.min_speech_duration_ms,
        min_silence_duration_ms=candidate.min_silence_duration_ms,
        speech_pad_ms=candidate.speech_pad_ms,
        return_seconds=False,
    )
    if not timestamps:
        return None
    first_start_sample = int(timestamps[0]["start"])
    return round(first_start_sample / TARGET_SAMPLE_RATE * 1000)


def _load_samples(
    *,
    clips_dir: Path,
    metadata_csv: Path,
    stt_results_csv: Path,
) -> tuple[
    list[CalibrationSample],
    list[CalibrationSample],
    dict[str, Any],
]:
    metadata_rows = _read_csv(metadata_csv)
    stt_rows = _read_csv(stt_results_csv)
    metadata_by_filename = {
        row["파일명"]: row for row in metadata_rows
    }
    core_rows = [
        row for row in stt_rows if row.get("question_id", "").strip()
    ]
    positive_rows = [
        row for row in core_rows if row.get("status") == "success"
    ]
    empty_stt_rows = [
        row
        for row in core_rows
        if row.get("status") == "empty_transcript"
    ]

    silence_samples = round(
        TARGET_SAMPLE_RATE * PREPENDED_SILENCE_MS / 1000,
    )
    positive_samples: list[CalibrationSample] = []
    empty_stt_samples: list[CalibrationSample] = []
    missing_files: list[str] = []

    for stt_row in positive_rows:
        sample = _load_sample(
            stt_row=stt_row,
            metadata_by_filename=metadata_by_filename,
            clips_dir=clips_dir,
            missing_files=missing_files,
        )
        if sample is None:
            continue
        waveform = np.concatenate(
            (
                np.zeros(silence_samples, dtype=np.float32),
                sample.waveform,
            ),
        ).astype(np.float32, copy=False)
        positive_samples.append(
            CalibrationSample(
                filename=sample.filename,
                waveform=np.ascontiguousarray(waveform),
                subject_id=sample.subject_id,
                category=sample.category,
                manual_response_delay_ms=(
                    sample.manual_response_delay_ms
                ),
            ),
        )

    for stt_row in empty_stt_rows:
        sample = _load_sample(
            stt_row=stt_row,
            metadata_by_filename=metadata_by_filename,
            clips_dir=clips_dir,
            missing_files=missing_files,
        )
        if sample is not None:
            empty_stt_samples.append(sample)

    if missing_files:
        preview = "\n".join(missing_files[:10])
        raise FileNotFoundError(
            "검증 음성 파일이 누락되었습니다.\n" + preview,
        )
    if not positive_samples:
        raise RuntimeError("검증할 발화 양성 음성을 찾지 못했습니다.")

    category_counts = Counter(
        sample.category for sample in positive_samples
    )
    overlap_counts = Counter(
        metadata_by_filename[sample.filename].get("겹침여부", "")
        for sample in positive_samples
    )
    manual_delays_seconds = [
        sample.manual_response_delay_ms / 1000
        for sample in positive_samples
    ]
    return positive_samples, empty_stt_samples, {
        "positive_sample_count": len(positive_samples),
        "positive_selection": (
            "Google STT status=success and non-empty question_id"
        ),
        "empty_stt_audit_sample_count": len(empty_stt_samples),
        "empty_stt_audit_selection": (
            "Google STT status=empty_transcript and "
            "non-empty question_id"
        ),
        "category_counts": dict(sorted(category_counts.items())),
        "overlap_counts": dict(sorted(overlap_counts.items())),
        "manual_response_delay_seconds": {
            "minimum": round(min(manual_delays_seconds), 6),
            "median": round(
                float(np.median(manual_delays_seconds)),
                6,
            ),
            "maximum": round(max(manual_delays_seconds), 6),
        },
    }


def _load_sample(
    *,
    stt_row: dict[str, str],
    metadata_by_filename: dict[str, dict[str, str]],
    clips_dir: Path,
    missing_files: list[str],
) -> CalibrationSample | None:
    filename = stt_row["파일명"]
    metadata = metadata_by_filename.get(filename)
    if metadata is None:
        raise RuntimeError(f"클립 메타데이터 누락: {filename}")

    subject_id = metadata["대상자ID"]
    audio_path = clips_dir / subject_id / f"{filename}.wav"
    if not audio_path.is_file():
        missing_files.append(str(audio_path))
        return None

    processed = preprocess_audio(
        content=audio_path.read_bytes(),
        content_type="audio/wav",
        max_duration_seconds=70.0,
    )
    maximum_clip_samples = round(
        TARGET_SAMPLE_RATE * MAX_CALIBRATION_CLIP_SECONDS,
    )
    waveform = np.ascontiguousarray(
        processed.waveform[:maximum_clip_samples],
        dtype=np.float32,
    )
    delay_text = metadata["응답지연 시간"].strip()
    manual_response_delay_ms = (
        round(float(delay_text) * 1000) if delay_text else 0
    )
    return CalibrationSample(
        filename=filename,
        waveform=waveform,
        subject_id=subject_id,
        category=metadata["질문범주"],
        manual_response_delay_ms=manual_response_delay_ms,
    )


def _build_synthetic_nonspeech_samples() -> list[CalibrationSample]:
    sample_count = round(
        TARGET_SAMPLE_RATE * MAX_CALIBRATION_CLIP_SECONDS,
    )
    time_axis = np.arange(sample_count, dtype=np.float32) / float(
        TARGET_SAMPLE_RATE,
    )
    random = np.random.default_rng(seed=42)
    impulse_click = np.zeros(sample_count, dtype=np.float32)
    impulse_click[2 * TARGET_SAMPLE_RATE] = 0.5
    noise_burst = np.zeros(sample_count, dtype=np.float32)
    burst_start = 2 * TARGET_SAMPLE_RATE
    burst_length = round(0.04 * TARGET_SAMPLE_RATE)
    noise_burst[burst_start : burst_start + burst_length] = random.normal(
        0,
        0.05,
        burst_length,
    ).astype(np.float32)
    cases = {
        "silence": np.zeros(sample_count, dtype=np.float32),
        "white-noise-rms-0.0003": random.normal(
            0,
            0.0003,
            sample_count,
        ).astype(np.float32),
        "white-noise-rms-0.001": random.normal(
            0,
            0.001,
            sample_count,
        ).astype(np.float32),
        "white-noise-rms-0.003": random.normal(
            0,
            0.003,
            sample_count,
        ).astype(np.float32),
        "hum-60hz-amplitude-0.003": (
            0.003 * np.sin(2 * np.pi * 60 * time_axis)
        ).astype(np.float32),
        "tone-1000hz-amplitude-0.01": (
            0.01 * np.sin(2 * np.pi * 1000 * time_axis)
        ).astype(np.float32),
        "impulse-click-amplitude-0.5": impulse_click,
        "broadband-noise-burst-40ms-rms-0.05": noise_burst,
    }
    return [
        CalibrationSample(
            filename=name,
            waveform=np.ascontiguousarray(waveform),
        )
        for name, waveform in cases.items()
    ]


def _read_csv(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        raise FileNotFoundError(f"CSV 파일을 찾을 수 없습니다: {path}")
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "수동 답변 시작점 기준 자료로 Silero VAD 후보를 비교합니다."
        ),
    )
    parser.add_argument("--clips-dir", type=Path, required=True)
    parser.add_argument("--metadata-csv", type=Path, required=True)
    parser.add_argument("--stt-results-csv", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("validation/vad-calibration-v1.json"),
    )
    return parser.parse_args()


if __name__ == "__main__":
    main()
