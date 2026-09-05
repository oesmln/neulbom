import asyncio
import hashlib
from datetime import UTC, datetime, timedelta

import httpx
import pytest

from app.audio.downloader import (
    AudioDownloadError,
    AudioDownloadReason,
    SignedAudioDownloader,
)

AUDIO_CONTENT = b"test-audio-content"


def test_downloads_audio_and_verifies_checksum() -> None:
    expected_sha256 = hashlib.sha256(
        AUDIO_CONTENT,
    ).hexdigest()

    async def scenario():
        transport = httpx.MockTransport(
            lambda request: httpx.Response(
                status_code=200,
                headers={
                    "content-type": "audio/mp4",
                    "content-length": str(
                        len(AUDIO_CONTENT),
                    ),
                },
                content=AUDIO_CONTENT,
                request=request,
            ),
        )

        async with httpx.AsyncClient(
            transport=transport,
        ) as client:
            downloader = SignedAudioDownloader(
                client=client,
                max_size_bytes=1024,
            )

            return await downloader.download(
                signed_url=(
                    "https://storage.example/"
                    "audio.m4a?signature=secret"
                ),
                expires_at=_future_expiration(),
                declared_content_type="audio/x-m4a",
                declared_size_bytes=len(
                    AUDIO_CONTENT,
                ),
                expected_sha256=expected_sha256,
            )

    result = asyncio.run(scenario())

    assert result.content == AUDIO_CONTENT
    assert result.content_type == "audio/mp4"
    assert result.size_bytes == len(AUDIO_CONTENT)
    assert result.sha256 == expected_sha256


def test_rejects_expired_url_without_request() -> None:
    request_count = 0

    def handler(
        request: httpx.Request,
    ) -> httpx.Response:
        nonlocal request_count
        request_count += 1
        return httpx.Response(
            200,
            content=AUDIO_CONTENT,
            request=request,
        )

    async def scenario() -> None:
        transport = httpx.MockTransport(handler)

        async with httpx.AsyncClient(
            transport=transport,
        ) as client:
            downloader = SignedAudioDownloader(
                client=client,
                max_size_bytes=1024,
            )

            with pytest.raises(
                AudioDownloadError,
            ) as captured:
                await downloader.download(
                    signed_url=(
                        "https://storage.example/audio.wav"
                    ),
                    expires_at=(
                        datetime.now(UTC)
                        - timedelta(seconds=1)
                    ),
                    declared_content_type="audio/wav",
                    declared_size_bytes=len(
                        AUDIO_CONTENT,
                    ),
                )

            assert captured.value.reason_code == (
                AudioDownloadReason
                .AUDIO_URL_EXPIRED
            )
            assert captured.value.retryable is True

    asyncio.run(scenario())
    assert request_count == 0


def test_maps_forbidden_response_to_expired_url() -> None:
    async def scenario() -> None:
        transport = httpx.MockTransport(
            lambda request: httpx.Response(
                403,
                request=request,
            ),
        )

        async with httpx.AsyncClient(
            transport=transport,
        ) as client:
            downloader = SignedAudioDownloader(
                client=client,
                max_size_bytes=1024,
            )

            with pytest.raises(
                AudioDownloadError,
            ) as captured:
                await downloader.download(
                    signed_url=(
                        "https://storage.example/audio.wav"
                    ),
                    expires_at=_future_expiration(),
                    declared_content_type="audio/wav",
                    declared_size_bytes=10,
                )

            assert captured.value.reason_code == (
                AudioDownloadReason
                .AUDIO_URL_EXPIRED
            )
            assert captured.value.retryable is True

    asyncio.run(scenario())


def test_rejects_unsupported_content_type() -> None:
    async def scenario() -> None:
        async with httpx.AsyncClient() as client:
            downloader = SignedAudioDownloader(
                client=client,
                max_size_bytes=1024,
            )

            with pytest.raises(
                AudioDownloadError,
            ) as captured:
                await downloader.download(
                    signed_url=(
                        "https://storage.example/audio.webm"
                    ),
                    expires_at=_future_expiration(),
                    declared_content_type="audio/webm",
                    declared_size_bytes=10,
                )

            assert captured.value.reason_code == (
                AudioDownloadReason
                .UNSUPPORTED_AUDIO_FORMAT
            )
            assert captured.value.retryable is False

    asyncio.run(scenario())


def test_rejects_audio_larger_than_limit() -> None:
    content = b"12345"

    async def scenario() -> None:
        transport = httpx.MockTransport(
            lambda request: httpx.Response(
                200,
                headers={
                    "content-type": "audio/wav",
                },
                content=content,
                request=request,
            ),
        )

        async with httpx.AsyncClient(
            transport=transport,
        ) as client:
            downloader = SignedAudioDownloader(
                client=client,
                max_size_bytes=4,
            )

            with pytest.raises(
                AudioDownloadError,
            ) as captured:
                await downloader.download(
                    signed_url=(
                        "https://storage.example/audio.wav"
                    ),
                    expires_at=_future_expiration(),
                    declared_content_type="audio/wav",
                    declared_size_bytes=4,
                )

            assert captured.value.reason_code == (
                AudioDownloadReason
                .AUDIO_DOWNLOAD_FAILED
            )
            assert captured.value.retryable is False

    asyncio.run(scenario())


def test_rejects_size_mismatch() -> None:
    async def scenario() -> None:
        transport = httpx.MockTransport(
            lambda request: httpx.Response(
                200,
                headers={
                    "content-type": "audio/mpeg",
                },
                content=AUDIO_CONTENT,
                request=request,
            ),
        )

        async with httpx.AsyncClient(
            transport=transport,
        ) as client:
            downloader = SignedAudioDownloader(
                client=client,
                max_size_bytes=1024,
            )

            with pytest.raises(
                AudioDownloadError,
            ) as captured:
                await downloader.download(
                    signed_url=(
                        "https://storage.example/audio.mp3"
                    ),
                    expires_at=_future_expiration(),
                    declared_content_type="audio/mpeg",
                    declared_size_bytes=1,
                )

            assert captured.value.reason_code == (
                AudioDownloadReason
                .AUDIO_DOWNLOAD_FAILED
            )
            assert captured.value.retryable is True

    asyncio.run(scenario())


def test_error_does_not_expose_signed_url() -> None:
    signed_url = (
        "https://storage.example/audio.wav"
        "?signature=top-secret"
    )

    async def scenario() -> None:
        transport = httpx.MockTransport(
            lambda request: httpx.Response(
                500,
                request=request,
            ),
        )

        async with httpx.AsyncClient(
            transport=transport,
        ) as client:
            downloader = SignedAudioDownloader(
                client=client,
                max_size_bytes=1024,
            )

            with pytest.raises(
                AudioDownloadError,
            ) as captured:
                await downloader.download(
                    signed_url=signed_url,
                    expires_at=_future_expiration(),
                    declared_content_type="audio/wav",
                    declared_size_bytes=10,
                )

            assert signed_url not in str(
                captured.value,
            )
            assert "top-secret" not in str(
                captured.value,
            )

    asyncio.run(scenario())


def _future_expiration() -> datetime:
    return (
        datetime.now(UTC)
        + timedelta(minutes=30)
    )