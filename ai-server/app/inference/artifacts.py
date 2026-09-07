import json
from dataclasses import dataclass
from json import JSONDecodeError
from pathlib import Path
from typing import Any

EXPECTED_SEEDS = (42, 52, 62)

AST_DIRECTORY_NAME = (
    "final_ast_service_21subjects_"
    "4layer_specaug_seed_ensemble_v1"
)
KCELECTRA_DIRECTORY_NAME = (
    "final_kcelectra_service_"
    "352clips_seed_ensemble_v1"
)
FUSION_DIRECTORY_NAME = (
    "final_fusion_lr_21subjects_core4_ast_v1"
)

EXPECTED_FUSION_FEATURE_ORDER = (
    "ast_oof_logit",
    "kcelectra_oof_logit",
    "category_balanced_wrong_event_score",
    "category_balanced_median_delay",
)


class ArtifactValidationError(ValueError):
    """모델 아티팩트 구조나 버전이 잘못된 경우 발생한다."""


@dataclass(frozen=True, slots=True)
class AstSeedArtifacts:
    seed: int
    directory: Path
    model_path: Path
    config_path: Path
    preprocessor_config_path: Path
    training_complete_path: Path


@dataclass(frozen=True, slots=True)
class KcElectraSeedArtifacts:
    seed: int
    directory: Path
    model_path: Path
    config_path: Path
    tokenizer_path: Path
    tokenizer_config_path: Path
    training_complete_path: Path


@dataclass(frozen=True, slots=True)
class ModelArtifactBundle:
    root: Path

    ast_directory: Path
    ast_ensemble_config_path: Path
    ast_seeds: tuple[AstSeedArtifacts, ...]

    kcelectra_directory: Path
    kcelectra_ensemble_config_path: Path
    kcelectra_runtime_versions_path: Path | None
    kcelectra_seeds: tuple[
        KcElectraSeedArtifacts,
        ...,
    ]

    fusion_directory: Path
    fusion_pipeline_path: Path
    fusion_contract_path: Path

    transformers_version: str
    ast_sampling_rate: int
    kcelectra_max_length: int
    fusion_feature_order: tuple[str, ...]
    training_default_threshold: float


def discover_model_artifacts(
    root: Path,
) -> ModelArtifactBundle:
    """모델 파일을 로딩하지 않고 구조와 버전을 검증한다."""
    resolved_root = root.resolve()
    _require_directory(
        resolved_root,
        "모델 아티팩트 루트",
    )

    (
        ast_directory,
        ast_ensemble_path,
        ast_seed_artifacts,
        ast_transformers_version,
        ast_sampling_rate,
    ) = _validate_ast_artifacts(resolved_root)

    (
        kcelectra_directory,
        kcelectra_ensemble_path,
        runtime_versions_path,
        kcelectra_seed_artifacts,
        kcelectra_transformers_version,
        kcelectra_max_length,
    ) = _validate_kcelectra_artifacts(
        resolved_root,
    )

    (
        fusion_directory,
        fusion_pipeline_path,
        fusion_contract_path,
        fusion_feature_order,
        training_default_threshold,
    ) = _validate_fusion_artifacts(
        resolved_root,
    )

    _require_equal(
        "AST와 KcELECTRA transformers_version",
        kcelectra_transformers_version,
        ast_transformers_version,
    )

    return ModelArtifactBundle(
        root=resolved_root,
        ast_directory=ast_directory,
        ast_ensemble_config_path=ast_ensemble_path,
        ast_seeds=ast_seed_artifacts,
        kcelectra_directory=kcelectra_directory,
        kcelectra_ensemble_config_path=(
            kcelectra_ensemble_path
        ),
        kcelectra_runtime_versions_path=(
            runtime_versions_path
        ),
        kcelectra_seeds=kcelectra_seed_artifacts,
        fusion_directory=fusion_directory,
        fusion_pipeline_path=fusion_pipeline_path,
        fusion_contract_path=fusion_contract_path,
        transformers_version=ast_transformers_version,
        ast_sampling_rate=ast_sampling_rate,
        kcelectra_max_length=kcelectra_max_length,
        fusion_feature_order=fusion_feature_order,
        training_default_threshold=(
            training_default_threshold
        ),
    )


def _validate_ast_artifacts(
    root: Path,
) -> tuple[
    Path,
    Path,
    tuple[AstSeedArtifacts, ...],
    str,
    int,
]:
    ast_directory = (
        root
        / "ast"
        / AST_DIRECTORY_NAME
    )
    _require_directory(
        ast_directory,
        "AST ensemble 디렉터리",
    )

    ensemble_path = (
        ast_directory / "ensemble_config.json"
    )
    ensemble = _load_json(
        ensemble_path,
        "AST ensemble config",
    )

    _require_json_value(
        ensemble,
        ("status",),
        "complete",
        "AST ensemble status",
    )
    _require_json_value(
        ensemble,
        ("purpose",),
        "final_service_ast_ensemble",
        "AST ensemble purpose",
    )
    _require_json_value(
        ensemble,
        (
            "training_contract",
            "model_seeds",
        ),
        list(EXPECTED_SEEDS),
        "AST model seeds",
    )

    seed_artifacts: list[AstSeedArtifacts] = []
    transformers_versions: set[str] = set()
    sampling_rates: set[int] = set()

    for seed in EXPECTED_SEEDS:
        seed_directory = (
            ast_directory / f"seed_{seed}"
        )
        _require_directory(
            seed_directory,
            f"AST seed {seed} 디렉터리",
        )

        model_path = (
            seed_directory / "model.safetensors"
        )
        config_path = (
            seed_directory / "config.json"
        )
        preprocessor_path = (
            seed_directory
            / "preprocessor_config.json"
        )
        training_complete_path = (
            seed_directory
            / "training_complete.json"
        )

        _require_nonempty_file(
            model_path,
            f"AST seed {seed} model",
        )

        config = _load_json(
            config_path,
            f"AST seed {seed} config",
        )
        preprocessor = _load_json(
            preprocessor_path,
            f"AST seed {seed} preprocessor",
        )
        training_complete = _load_json(
            training_complete_path,
            f"AST seed {seed} training metadata",
        )

        _require_json_value(
            config,
            ("model_type",),
            "audio-spectrogram-transformer",
            f"AST seed {seed} model_type",
        )
        _require_json_value(
            config,
            ("label2id",),
            {
                "정상": 0,
                "치매": 1,
            },
            f"AST seed {seed} label2id",
        )
        _require_json_value(
            preprocessor,
            ("feature_extractor_type",),
            "ASTFeatureExtractor",
            f"AST seed {seed} feature extractor",
        )
        _require_json_value(
            training_complete,
            ("seed",),
            seed,
            f"AST seed {seed} training seed",
        )

        transformers_version = _get_json_value(
            config,
            ("transformers_version",),
            f"AST seed {seed} transformers_version",
        )
        sampling_rate = _get_json_value(
            preprocessor,
            ("sampling_rate",),
            f"AST seed {seed} sampling_rate",
        )

        if not isinstance(
            transformers_version,
            str,
        ):
            raise ArtifactValidationError(
                f"AST seed {seed} transformers_version은 "
                "문자열이어야 합니다.",
            )

        if not isinstance(
            sampling_rate,
            int,
        ):
            raise ArtifactValidationError(
                f"AST seed {seed} sampling_rate는 "
                "정수여야 합니다.",
            )

        transformers_versions.add(
            transformers_version,
        )
        sampling_rates.add(sampling_rate)

        seed_artifacts.append(
            AstSeedArtifacts(
                seed=seed,
                directory=seed_directory,
                model_path=model_path,
                config_path=config_path,
                preprocessor_config_path=(
                    preprocessor_path
                ),
                training_complete_path=(
                    training_complete_path
                ),
            ),
        )

    _require_single_value(
        "AST transformers_version",
        transformers_versions,
    )
    _require_single_value(
        "AST sampling_rate",
        sampling_rates,
    )

    return (
        ast_directory,
        ensemble_path,
        tuple(seed_artifacts),
        next(iter(transformers_versions)),
        next(iter(sampling_rates)),
    )


def _validate_kcelectra_artifacts(
    root: Path,
) -> tuple[
    Path,
    Path,
    Path | None,
    tuple[KcElectraSeedArtifacts, ...],
    str,
    int,
]:
    kcelectra_directory = (
        root
        / "kcelectra"
        / KCELECTRA_DIRECTORY_NAME
    )
    _require_directory(
        kcelectra_directory,
        "KcELECTRA ensemble 디렉터리",
    )

    ensemble_path = (
        kcelectra_directory
        / "ensemble_config.json"
    )
    run_status_path = (
        kcelectra_directory
        / "run_status.json"
    )

    ensemble = _load_json(
        ensemble_path,
        "KcELECTRA ensemble config",
    )
    run_status = _load_json(
        run_status_path,
        "KcELECTRA run status",
    )

    _require_json_value(
        ensemble,
        ("status",),
        "complete",
        "KcELECTRA ensemble status",
    )
    _require_json_value(
        ensemble,
        ("purpose",),
        "final_service_kcelectra_ensemble",
        "KcELECTRA ensemble purpose",
    )
    _require_json_value(
        ensemble,
        ("model_seeds",),
        list(EXPECTED_SEEDS),
        "KcELECTRA model seeds",
    )
    _require_json_value(
        run_status,
        ("status",),
        "complete",
        "KcELECTRA run status",
    )
    _require_json_value(
        run_status,
        ("completed_seeds",),
        list(EXPECTED_SEEDS),
        "KcELECTRA completed seeds",
    )

    maximum_length = _get_json_value(
        ensemble,
        (
            "fixed_design",
            "max_length",
        ),
        "KcELECTRA inference max_length",
    )

    if not isinstance(maximum_length, int):
        raise ArtifactValidationError(
            "KcELECTRA max_length는 정수여야 합니다.",
        )

    runtime_versions_path = (
        kcelectra_directory
        / "runtime_versions.json"
    )

    if runtime_versions_path.exists():
        _load_json(
            runtime_versions_path,
            "KcELECTRA runtime versions",
        )
    else:
        runtime_versions_path = None

    seed_artifacts: list[
        KcElectraSeedArtifacts
    ] = []
    transformers_versions: set[str] = set()

    for seed in EXPECTED_SEEDS:
        seed_directory = (
            kcelectra_directory
            / f"seed_{seed}"
        )
        _require_directory(
            seed_directory,
            f"KcELECTRA seed {seed} 디렉터리",
        )

        model_path = (
            seed_directory / "model.safetensors"
        )
        config_path = (
            seed_directory / "config.json"
        )
        tokenizer_path = (
            seed_directory / "tokenizer.json"
        )
        tokenizer_config_path = (
            seed_directory
            / "tokenizer_config.json"
        )
        training_complete_path = (
            seed_directory
            / "training_complete.json"
        )

        _require_nonempty_file(
            model_path,
            f"KcELECTRA seed {seed} model",
        )
        _require_nonempty_file(
            tokenizer_path,
            f"KcELECTRA seed {seed} tokenizer",
        )

        config = _load_json(
            config_path,
            f"KcELECTRA seed {seed} config",
        )
        tokenizer_config = _load_json(
            tokenizer_config_path,
            f"KcELECTRA seed {seed} tokenizer config",
        )
        training_complete = _load_json(
            training_complete_path,
            f"KcELECTRA seed {seed} training metadata",
        )

        _require_json_value(
            config,
            ("model_type",),
            "electra",
            f"KcELECTRA seed {seed} model_type",
        )
        _require_json_value(
            config,
            ("label2id",),
            {
                "dementia": 1,
                "normal": 0,
            },
            f"KcELECTRA seed {seed} label2id",
        )
        _require_json_value(
            tokenizer_config,
            ("tokenizer_class",),
            "BertTokenizer",
            f"KcELECTRA seed {seed} tokenizer class",
        )
        _require_json_value(
            training_complete,
            ("seed",),
            seed,
            f"KcELECTRA seed {seed} training seed",
        )

        transformers_version = _get_json_value(
            config,
            ("transformers_version",),
            (
                "KcELECTRA seed "
                f"{seed} transformers_version"
            ),
        )

        if not isinstance(
            transformers_version,
            str,
        ):
            raise ArtifactValidationError(
                "KcELECTRA transformers_version은 "
                "문자열이어야 합니다.",
            )

        transformers_versions.add(
            transformers_version,
        )

        seed_artifacts.append(
            KcElectraSeedArtifacts(
                seed=seed,
                directory=seed_directory,
                model_path=model_path,
                config_path=config_path,
                tokenizer_path=tokenizer_path,
                tokenizer_config_path=(
                    tokenizer_config_path
                ),
                training_complete_path=(
                    training_complete_path
                ),
            ),
        )

    _require_single_value(
        "KcELECTRA transformers_version",
        transformers_versions,
    )

    return (
        kcelectra_directory,
        ensemble_path,
        runtime_versions_path,
        tuple(seed_artifacts),
        next(iter(transformers_versions)),
        maximum_length,
    )


def _validate_fusion_artifacts(
    root: Path,
) -> tuple[
    Path,
    Path,
    Path,
    tuple[str, ...],
    float,
]:
    fusion_directory = (
        root
        / "fusion"
        / FUSION_DIRECTORY_NAME
    )
    _require_directory(
        fusion_directory,
        "fusion 디렉터리",
    )

    pipeline_path = (
        fusion_directory
        / "final_fusion_lr_pipeline.joblib"
    )
    contract_path = (
        fusion_directory
        / "final_fusion_lr_contract.json"
    )

    _require_nonempty_file(
        pipeline_path,
        "fusion pipeline",
    )
    contract = _load_json(
        contract_path,
        "fusion contract",
    )

    feature_order = _get_json_value(
        contract,
        ("feature_order",),
        "fusion feature_order",
    )
    training_default_threshold = (
        _get_json_value(
            contract,
            ("default_threshold",),
            "fusion training default_threshold",
        )
    )
    class_order = _get_json_value(
        contract,
        ("class_order",),
        "fusion class_order",
    )

    _require_equal(
        "fusion feature_order",
        feature_order,
        list(EXPECTED_FUSION_FEATURE_ORDER),
    )
    _require_equal(
        "fusion class_order",
        class_order,
        [0, 1],
    )
    _require_equal(
        "fusion training default_threshold",
        training_default_threshold,
        0.5,
    )

    return (
        fusion_directory,
        pipeline_path,
        contract_path,
        tuple(feature_order),
        float(training_default_threshold),
    )


def _require_directory(
    path: Path,
    name: str,
) -> None:
    if not path.is_dir():
        raise ArtifactValidationError(
            f"{name}를 찾을 수 없습니다: {path}",
        )


def _require_nonempty_file(
    path: Path,
    name: str,
) -> None:
    if not path.is_file():
        raise ArtifactValidationError(
            f"{name} 파일을 찾을 수 없습니다: {path}",
        )

    if path.stat().st_size <= 0:
        raise ArtifactValidationError(
            f"{name} 파일이 비어 있습니다: {path}",
        )


def _load_json(
    path: Path,
    name: str,
) -> dict[str, Any]:
    _require_nonempty_file(path, name)

    try:
        data = json.loads(
            path.read_text(encoding="utf-8"),
        )
    except UnicodeDecodeError as error:
        raise ArtifactValidationError(
            f"{name}은 UTF-8이어야 합니다: {path}",
        ) from error
    except JSONDecodeError as error:
        raise ArtifactValidationError(
            f"{name} JSON 문법이 잘못되었습니다: "
            f"{path} "
            f"(line={error.lineno}, column={error.colno})",
        ) from error
    except OSError as error:
        raise ArtifactValidationError(
            f"{name}을 읽을 수 없습니다: {path}",
        ) from error

    if not isinstance(data, dict):
        raise ArtifactValidationError(
            f"{name}의 최상위 값은 객체여야 합니다: {path}",
        )

    return data


def _get_json_value(
    data: dict[str, Any],
    keys: tuple[str, ...],
    name: str,
) -> Any:
    current: Any = data

    for key in keys:
        if not isinstance(current, dict):
            raise ArtifactValidationError(
                f"{name} 경로가 올바르지 않습니다: "
                f"{'.'.join(keys)}",
            )

        if key not in current:
            raise ArtifactValidationError(
                f"{name} 필드가 없습니다: "
                f"{'.'.join(keys)}",
            )

        current = current[key]

    return current


def _require_json_value(
    data: dict[str, Any],
    keys: tuple[str, ...],
    expected: Any,
    name: str,
) -> None:
    actual = _get_json_value(
        data,
        keys,
        name,
    )
    _require_equal(
        name,
        actual,
        expected,
    )


def _require_equal(
    name: str,
    actual: Any,
    expected: Any,
) -> None:
    if actual != expected:
        raise ArtifactValidationError(
            f"{name}가 일치하지 않습니다: "
            f"actual={actual!r}, expected={expected!r}",
        )


def _require_single_value(
    name: str,
    values: set[Any],
) -> None:
    if len(values) != 1:
        raise ArtifactValidationError(
            f"{name}이 seed 간 일치하지 않습니다: "
            f"{sorted(values, key=str)}",
        )