import json
from pathlib import Path

import pytest

from app.inference.artifacts import (
    AST_DIRECTORY_NAME,
    EXPECTED_FUSION_FEATURE_ORDER,
    FUSION_DIRECTORY_NAME,
    KCELECTRA_DIRECTORY_NAME,
    ArtifactValidationError,
    discover_model_artifacts,
)


def test_discovers_valid_model_artifacts(
    tmp_path: Path,
) -> None:
    _create_valid_artifact_tree(tmp_path)

    bundle = discover_model_artifacts(tmp_path)

    assert len(bundle.ast_seeds) == 3
    assert len(bundle.kcelectra_seeds) == 3
    assert bundle.transformers_version == "5.15.1"
    assert bundle.ast_sampling_rate == 16000
    assert bundle.kcelectra_max_length == 256
    assert bundle.decision_threshold == 0.5
    assert bundle.fusion_feature_order == (
        EXPECTED_FUSION_FEATURE_ORDER
    )


def test_accepts_missing_optional_runtime_versions(
    tmp_path: Path,
) -> None:
    _create_valid_artifact_tree(
        tmp_path,
        include_runtime_versions=False,
    )

    bundle = discover_model_artifacts(tmp_path)

    assert (
        bundle.kcelectra_runtime_versions_path
        is None
    )


def test_rejects_missing_model_weight(
    tmp_path: Path,
) -> None:
    _create_valid_artifact_tree(tmp_path)

    missing_model = (
        tmp_path
        / "ast"
        / AST_DIRECTORY_NAME
        / "seed_52"
        / "model.safetensors"
    )
    missing_model.unlink()

    with pytest.raises(
        ArtifactValidationError,
        match="AST seed 52 model 파일을 찾을 수 없습니다",
    ):
        discover_model_artifacts(tmp_path)


def test_rejects_empty_fusion_pipeline(
    tmp_path: Path,
) -> None:
    _create_valid_artifact_tree(tmp_path)

    pipeline = (
        tmp_path
        / "fusion"
        / FUSION_DIRECTORY_NAME
        / "final_fusion_lr_pipeline.joblib"
    )
    pipeline.write_bytes(b"")

    with pytest.raises(
        ArtifactValidationError,
        match="fusion pipeline 파일이 비어 있습니다",
    ):
        discover_model_artifacts(tmp_path)


def test_rejects_wrong_ast_seed_configuration(
    tmp_path: Path,
) -> None:
    _create_valid_artifact_tree(tmp_path)

    ensemble_path = (
        tmp_path
        / "ast"
        / AST_DIRECTORY_NAME
        / "ensemble_config.json"
    )
    ensemble = _read_json(ensemble_path)
    ensemble["training_contract"]["model_seeds"] = [
        42,
        52,
    ]
    _write_json(ensemble_path, ensemble)

    with pytest.raises(
        ArtifactValidationError,
        match="AST model seeds",
    ):
        discover_model_artifacts(tmp_path)


def test_rejects_inconsistent_transformers_version(
    tmp_path: Path,
) -> None:
    _create_valid_artifact_tree(tmp_path)

    config_path = (
        tmp_path
        / "kcelectra"
        / KCELECTRA_DIRECTORY_NAME
        / "seed_62"
        / "config.json"
    )
    config = _read_json(config_path)
    config["transformers_version"] = "different"
    _write_json(config_path, config)

    with pytest.raises(
        ArtifactValidationError,
        match="seed 간 일치하지 않습니다",
    ):
        discover_model_artifacts(tmp_path)


def test_rejects_wrong_fusion_feature_order(
    tmp_path: Path,
) -> None:
    _create_valid_artifact_tree(tmp_path)

    contract_path = (
        tmp_path
        / "fusion"
        / FUSION_DIRECTORY_NAME
        / "final_fusion_lr_contract.json"
    )
    contract = _read_json(contract_path)
    contract["feature_order"] = list(
        reversed(EXPECTED_FUSION_FEATURE_ORDER),
    )
    _write_json(contract_path, contract)

    with pytest.raises(
        ArtifactValidationError,
        match="fusion feature_order",
    ):
        discover_model_artifacts(tmp_path)


def _create_valid_artifact_tree(
    root: Path,
    *,
    include_runtime_versions: bool = True,
) -> None:
    _create_ast_artifacts(root)
    _create_kcelectra_artifacts(
        root,
        include_runtime_versions=(
            include_runtime_versions
        ),
    )
    _create_fusion_artifacts(root)


def _create_ast_artifacts(
    root: Path,
) -> None:
    ast_directory = (
        root
        / "ast"
        / AST_DIRECTORY_NAME
    )
    ast_directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    _write_json(
        ast_directory / "ensemble_config.json",
        {
            "status": "complete",
            "purpose": "final_service_ast_ensemble",
            "training_contract": {
                "model_seeds": [42, 52, 62],
            },
        },
    )

    for seed in (42, 52, 62):
        seed_directory = (
            ast_directory / f"seed_{seed}"
        )
        seed_directory.mkdir()

        (
            seed_directory / "model.safetensors"
        ).write_bytes(b"fake-ast-model")

        _write_json(
            seed_directory / "config.json",
            {
                "model_type": (
                    "audio-spectrogram-transformer"
                ),
                "label2id": {
                    "정상": 0,
                    "치매": 1,
                },
                "transformers_version": "5.15.1",
            },
        )
        _write_json(
            seed_directory
            / "preprocessor_config.json",
            {
                "feature_extractor_type": (
                    "ASTFeatureExtractor"
                ),
                "sampling_rate": 16000,
            },
        )
        _write_json(
            seed_directory
            / "training_complete.json",
            {
                "seed": seed,
            },
        )


def _create_kcelectra_artifacts(
    root: Path,
    *,
    include_runtime_versions: bool,
) -> None:
    kcelectra_directory = (
        root
        / "kcelectra"
        / KCELECTRA_DIRECTORY_NAME
    )
    kcelectra_directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    _write_json(
        kcelectra_directory
        / "ensemble_config.json",
        {
            "status": "complete",
            "purpose": (
                "final_service_kcelectra_ensemble"
            ),
            "model_seeds": [42, 52, 62],
            "fixed_design": {
                "max_length": 256,
            },
        },
    )
    _write_json(
        kcelectra_directory / "run_status.json",
        {
            "status": "complete",
            "completed_seeds": [42, 52, 62],
        },
    )

    if include_runtime_versions:
        _write_json(
            kcelectra_directory
            / "runtime_versions.json",
            {
                "python": "3.13.15",
                "torch": "2.11.0+cu128",
                "transformers": "5.15.1",
            },
        )

    for seed in (42, 52, 62):
        seed_directory = (
            kcelectra_directory
            / f"seed_{seed}"
        )
        seed_directory.mkdir()

        (
            seed_directory / "model.safetensors"
        ).write_bytes(b"fake-kcelectra-model")
        (
            seed_directory / "tokenizer.json"
        ).write_bytes(b"fake-tokenizer")

        _write_json(
            seed_directory / "config.json",
            {
                "model_type": "electra",
                "label2id": {
                    "dementia": 1,
                    "normal": 0,
                },
                "transformers_version": "5.15.1",
            },
        )
        _write_json(
            seed_directory
            / "tokenizer_config.json",
            {
                "tokenizer_class": "BertTokenizer",
                "model_max_length": 512,
            },
        )
        _write_json(
            seed_directory
            / "training_complete.json",
            {
                "seed": seed,
            },
        )


def _create_fusion_artifacts(
    root: Path,
) -> None:
    fusion_directory = (
        root
        / "fusion"
        / FUSION_DIRECTORY_NAME
    )
    fusion_directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    (
        fusion_directory
        / "final_fusion_lr_pipeline.joblib"
    ).write_bytes(b"fake-fusion-pipeline")

    _write_json(
        fusion_directory
        / "final_fusion_lr_contract.json",
        {
            "feature_order": list(
                EXPECTED_FUSION_FEATURE_ORDER,
            ),
            "class_order": [0, 1],
            "default_threshold": 0.5,
        },
    )


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