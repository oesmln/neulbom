import asyncio
import time
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

from app.contracts.loader import (
    load_contract_bundle,
)
from app.core.config import PROJECT_ROOT
from app.inference.artifacts import (
    ArtifactValidationError,
)
from app.repositories.analysis import (
    AnalysisStatus,
    StoredAnalysis,
)
from app.services.analysis_runtime import (
    LazySessionAnalysisProcessor,
)
from app.services.analysis_worker import (
    AnalysisCompleted,
    AnalysisModelUnavailableError,
)


class DummyDownloader:
    pass


class FakeProcessor:
    def __init__(self) -> None:
        self.process_count = 0

    async def process(
        self,
        analysis: StoredAnalysis,
    ) -> AnalysisCompleted:
        self.process_count += 1

        return AnalysisCompleted(
            result_body={
                "analysis_id": str(
                    analysis.analysis_id,
                ),
            },
        )


def create_analysis() -> StoredAnalysis:
    now = datetime.now(UTC)

    return StoredAnalysis(
        analysis_id=uuid4(),
        assessment_id=uuid4(),
        status=AnalysisStatus.PROCESSING,
        request_body={},
        retryable=False,
        reason_code=None,
        retry_items=(),
        result_body=None,
        created_at=now,
        updated_at=now,
    )


def create_lazy_processor(
    *,
    artifacts_dir: Path,
    processor_factory,
) -> LazySessionAnalysisProcessor:
    contracts = load_contract_bundle(
        PROJECT_ROOT / "contracts",
    )

    return LazySessionAnalysisProcessor(
        contracts=contracts,
        audio_downloader=DummyDownloader(),
        artifacts_dir=artifacts_dir,
        processor_factory=(
            processor_factory
        ),
    )


def test_loads_processor_only_once(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        fake_processor = FakeProcessor()
        factory_count = 0

        def factory():
            nonlocal factory_count
            factory_count += 1
            return fake_processor

        lazy_processor = create_lazy_processor(
            artifacts_dir=tmp_path,
            processor_factory=factory,
        )

        first = await lazy_processor.process(
            create_analysis(),
        )
        second = await lazy_processor.process(
            create_analysis(),
        )

        assert isinstance(
            first,
            AnalysisCompleted,
        )
        assert isinstance(
            second,
            AnalysisCompleted,
        )
        assert factory_count == 1
        assert (
            fake_processor.process_count
            == 2
        )
        assert lazy_processor.is_loaded is True

    asyncio.run(scenario())


def test_concurrent_requests_load_once(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        fake_processor = FakeProcessor()
        factory_count = 0

        def factory():
            nonlocal factory_count
            factory_count += 1
            time.sleep(0.02)
            return fake_processor

        lazy_processor = create_lazy_processor(
            artifacts_dir=tmp_path,
            processor_factory=factory,
        )

        outcomes = await asyncio.gather(
            lazy_processor.process(
                create_analysis(),
            ),
            lazy_processor.process(
                create_analysis(),
            ),
            lazy_processor.process(
                create_analysis(),
            ),
        )

        assert factory_count == 1
        assert len(outcomes) == 3
        assert (
            fake_processor.process_count
            == 3
        )

    asyncio.run(scenario())


def test_model_loading_error_is_converted(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        def failing_factory():
            raise ArtifactValidationError(
                "missing artifacts",
            )

        lazy_processor = create_lazy_processor(
            artifacts_dir=tmp_path,
            processor_factory=(
                failing_factory
            ),
        )

        try:
            await lazy_processor.process(
                create_analysis(),
            )
        except (
            AnalysisModelUnavailableError
        ):
            pass
        else:
            raise AssertionError(
                "모델 로딩 오류가 변환되지 "
                "않았습니다.",
            )

        assert (
            lazy_processor.is_loaded
            is False
        )

    asyncio.run(scenario())


def test_retries_loading_after_failure(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        fake_processor = FakeProcessor()
        factory_count = 0

        def factory():
            nonlocal factory_count
            factory_count += 1

            if factory_count == 1:
                raise ArtifactValidationError(
                    "temporary failure",
                )

            return fake_processor

        lazy_processor = create_lazy_processor(
            artifacts_dir=tmp_path,
            processor_factory=factory,
        )

        try:
            await lazy_processor.process(
                create_analysis(),
            )
        except (
            AnalysisModelUnavailableError
        ):
            pass

        outcome = await lazy_processor.process(
            create_analysis(),
        )

        assert isinstance(
            outcome,
            AnalysisCompleted,
        )
        assert factory_count == 2
        assert lazy_processor.is_loaded is True

    asyncio.run(scenario())


def test_processor_error_is_not_hidden(
    tmp_path: Path,
) -> None:
    async def scenario() -> None:
        class FailingProcessor:
            async def process(
                self,
                analysis: StoredAnalysis,
            ):
                del analysis
                raise RuntimeError(
                    "processing failure",
                )

        lazy_processor = create_lazy_processor(
            artifacts_dir=tmp_path,
            processor_factory=(
                FailingProcessor
            ),
        )

        try:
            await lazy_processor.process(
                create_analysis(),
            )
        except RuntimeError as error:
            assert str(error) == (
                "processing failure"
            )
        else:
            raise AssertionError(
                "분석 처리 오류가 숨겨졌습니다.",
            )

    asyncio.run(scenario())