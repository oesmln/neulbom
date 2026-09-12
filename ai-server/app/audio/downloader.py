import asyncio
import hashlib
import ipaddress
import socket
from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from typing import Callable
from urllib.parse import urlsplit

import httpx


_CONTENT_TYPE_ALIASES = {
    "audio/wav": "audio/wav",
    "audio/x-wav": "audio/wav",
    "audio/mp4": "audio/mp4",
    "audio/x-m4a": "audio/mp4",
    "audio/mpeg": "audio/mpeg",
    "audio/webm": "audio/webm",
}

_LOCAL_AUDIO_HOSTS = frozenset(
    {
        "localhost",
        "127.0.0.1",
        "::1",
        "host.docker.internal",
        "gateway.docker.internal",
    },
)


class AudioDownloadReason(StrEnum):
    AUDIO_URL_EXPIRED = "AUDIO_URL_EXPIRED"
    AUDIO_DOWNLOAD_FAILED = "AUDIO_DOWNLOAD_FAILED"
    UNSUPPORTED_AUDIO_FORMAT = (
        "UNSUPPORTED_AUDIO_FORMAT"
    )


@dataclass(frozen=True, slots=True)
class DownloadedAudio:
    content: bytes
    content_type: str
    size_bytes: int
    sha256: str


class AudioDownloadError(RuntimeError):
    def __init__(
        self,
        *,
        reason_code: AudioDownloadReason,
        message: str,
        retryable: bool,
    ) -> None:
        super().__init__(message)
        self.reason_code = reason_code
        self.retryable = retryable


class SignedAudioDownloader:
    def __init__(
        self,
        *,
        client: httpx.AsyncClient,
        max_size_bytes: int,
        allowed_hosts: frozenset[str],
        allow_local_urls: bool = False,
        resolver: Callable[
            [str, int], list[str]
        ] | None = None,
    ) -> None:
        if max_size_bytes <= 0:
            raise ValueError(
                "max_size_bytes는 1 이상이어야 합니다.",
            )

        self._client = client
        self._max_size_bytes = max_size_bytes
        self._allow_local_urls = allow_local_urls
        self._allowed_hosts = frozenset(
            host.strip().lower().rstrip(".")
            for host in allowed_hosts
            if host.strip()
        )
        self._resolver = resolver or _resolve_host

    async def download(
        self,
        *,
        signed_url: str,
        expires_at: datetime,
        declared_content_type: str,
        declared_size_bytes: int,
        expected_sha256: str | None = None,
    ) -> DownloadedAudio:
        await _validate_signed_url(
            signed_url,
            allowed_hosts=self._allowed_hosts,
            resolver=self._resolver,
            allow_local_urls=self._allow_local_urls,
        )
        _validate_expiration(expires_at)

        content_type = _normalize_content_type(
            declared_content_type,
        )

        if declared_size_bytes <= 0:
            raise ValueError(
                "declared_size_bytes는 1 이상이어야 합니다.",
            )

        if declared_size_bytes > self._max_size_bytes:
            raise AudioDownloadError(
                reason_code=(
                    AudioDownloadReason
                    .AUDIO_DOWNLOAD_FAILED
                ),
                message=(
                    "음성 파일이 허용된 최대 크기를 "
                    "초과했습니다."
                ),
                retryable=False,
            )

        normalized_checksum = _normalize_checksum(
            expected_sha256,
        )

        try:
            async with self._client.stream(
                "GET",
                signed_url,
                headers={
                    "Accept": declared_content_type,
                },
            ) as response:
                _validate_response_status(response)

                response_content_type = (
                    response.headers.get(
                        "content-type",
                        declared_content_type,
                    )
                )
                downloaded_content_type = (
                    _normalize_content_type(
                        response_content_type,
                    )
                )

                if downloaded_content_type != content_type:
                    raise AudioDownloadError(
                        reason_code=(
                            AudioDownloadReason
                            .UNSUPPORTED_AUDIO_FORMAT
                        ),
                        message=(
                            "응답의 음성 형식이 요청에 "
                            "명시된 형식과 일치하지 않습니다."
                        ),
                        retryable=False,
                    )

                _validate_content_length(
                    response=response,
                    max_size_bytes=self._max_size_bytes,
                )

                content = bytearray()
                digest = hashlib.sha256()

                async for chunk in response.aiter_bytes():
                    if not chunk:
                        continue

                    content.extend(chunk)
                    digest.update(chunk)

                    if (
                        len(content)
                        > self._max_size_bytes
                    ):
                        raise AudioDownloadError(
                            reason_code=(
                                AudioDownloadReason
                                .AUDIO_DOWNLOAD_FAILED
                            ),
                            message=(
                                "음성 파일이 허용된 최대 "
                                "크기를 초과했습니다."
                            ),
                            retryable=False,
                        )

        except AudioDownloadError:
            raise
        except (
            httpx.TimeoutException,
            httpx.TransportError,
        ) as error:
            raise AudioDownloadError(
                reason_code=(
                    AudioDownloadReason
                    .AUDIO_DOWNLOAD_FAILED
                ),
                message="음성 파일을 다운로드하지 못했습니다.",
                retryable=True,
            ) from error

        downloaded_size = len(content)

        if downloaded_size != declared_size_bytes:
            raise AudioDownloadError(
                reason_code=(
                    AudioDownloadReason
                    .AUDIO_DOWNLOAD_FAILED
                ),
                message=(
                    "다운로드한 음성 파일 크기가 "
                    "요청 정보와 일치하지 않습니다."
                ),
                retryable=True,
            )

        downloaded_sha256 = digest.hexdigest()

        if (
            normalized_checksum is not None
            and downloaded_sha256
            != normalized_checksum
        ):
            raise AudioDownloadError(
                reason_code=(
                    AudioDownloadReason
                    .AUDIO_DOWNLOAD_FAILED
                ),
                message=(
                    "다운로드한 음성 파일의 "
                    "체크섬이 일치하지 않습니다."
                ),
                retryable=True,
            )

        return DownloadedAudio(
            content=bytes(content),
            content_type=content_type,
            size_bytes=downloaded_size,
            sha256=downloaded_sha256,
        )


def create_audio_http_client(
    *,
    timeout_seconds: float,
) -> httpx.AsyncClient:
    if timeout_seconds <= 0:
        raise ValueError(
            "timeout_seconds는 0보다 커야 합니다.",
        )

    return httpx.AsyncClient(
        timeout=httpx.Timeout(timeout_seconds),
        follow_redirects=False,
    )


async def _validate_signed_url(
    signed_url: str,
    *,
    allowed_hosts: frozenset[str],
    resolver: Callable[[str, int], list[str]],
    allow_local_urls: bool = False,
) -> None:
    parsed = urlsplit(signed_url)
    hostname = (
        parsed.hostname or ""
    ).lower().rstrip(".")
    try:
        port = parsed.port
    except ValueError:
        _raise_unsafe_url()

    scheme = parsed.scheme.lower()
    is_local_http = (
        allow_local_urls
        and scheme == "http"
        and hostname in _LOCAL_AUDIO_HOSTS
    )

    if (
        (scheme != "https" and not is_local_http)
        or not hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.fragment
        or (
            scheme == "https"
            and port not in {None, 443}
        )
    ):
        _raise_unsafe_url()

    if hostname not in allowed_hosts:
        _raise_unsafe_url()

    try:
        addresses = await asyncio.to_thread(
            resolver,
            hostname,
            port or (80 if is_local_http else 443),
        )
    except (OSError, ValueError) as error:
        raise AudioDownloadError(
            reason_code=AudioDownloadReason.AUDIO_DOWNLOAD_FAILED,
            message="음성 URL 호스트를 확인할 수 없습니다.",
            retryable=True,
        ) from error

    if not addresses:
        _raise_unsafe_url()

    for address in addresses:
        try:
            resolved_ip = ipaddress.ip_address(address)
        except ValueError:
            _raise_unsafe_url()
        if is_local_http:
            if not _is_allowed_local_address(
                resolved_ip,
            ):
                _raise_unsafe_url()
        elif not resolved_ip.is_global:
            _raise_unsafe_url()


def _is_allowed_local_address(
    address: ipaddress.IPv4Address | ipaddress.IPv6Address,
) -> bool:
    return (
        (address.is_loopback or address.is_private)
        and not address.is_link_local
        and not address.is_multicast
        and not address.is_reserved
        and not address.is_unspecified
    )


def _resolve_host(hostname: str, port: int) -> list[str]:
    return list({
        result[4][0]
        for result in socket.getaddrinfo(
            hostname,
            port,
            type=socket.SOCK_STREAM,
        )
    })


def _raise_unsafe_url() -> None:
    raise AudioDownloadError(
        reason_code=AudioDownloadReason.AUDIO_DOWNLOAD_FAILED,
        message="허용되지 않은 음성 URL입니다.",
        retryable=False,
    )


def _validate_expiration(
    expires_at: datetime,
) -> None:
    if (
        expires_at.tzinfo is None
        or expires_at.utcoffset() is None
    ):
        raise ValueError(
            "expires_at에는 시간대 정보가 필요합니다.",
        )

    now = datetime.now(
        tz=expires_at.tzinfo,
    )

    if expires_at <= now:
        raise AudioDownloadError(
            reason_code=(
                AudioDownloadReason
                .AUDIO_URL_EXPIRED
            ),
            message="음성 URL이 만료되었습니다.",
            retryable=True,
        )


def _normalize_content_type(
    content_type: str,
) -> str:
    base_content_type = (
        content_type
        .split(";", maxsplit=1)[0]
        .strip()
        .lower()
    )

    normalized = _CONTENT_TYPE_ALIASES.get(
        base_content_type,
    )

    if normalized is None:
        raise AudioDownloadError(
            reason_code=(
                AudioDownloadReason
                .UNSUPPORTED_AUDIO_FORMAT
            ),
            message="지원하지 않는 음성 형식입니다.",
            retryable=False,
        )

    return normalized


def _normalize_checksum(
    checksum: str | None,
) -> str | None:
    if checksum is None:
        return None

    normalized = checksum.strip().lower()

    if (
        len(normalized) != 64
        or any(
            character not in "0123456789abcdef"
            for character in normalized
        )
    ):
        raise ValueError(
            "SHA-256 체크섬 형식이 올바르지 않습니다.",
        )

    return normalized


def _validate_response_status(
    response: httpx.Response,
) -> None:
    if 200 <= response.status_code < 300:
        return

    if response.status_code in {401, 403}:
        raise AudioDownloadError(
            reason_code=(
                AudioDownloadReason
                .AUDIO_URL_EXPIRED
            ),
            message=(
                "음성 URL이 만료되었거나 "
                "접근 권한이 없습니다."
            ),
            retryable=True,
        )

    raise AudioDownloadError(
        reason_code=(
            AudioDownloadReason
            .AUDIO_DOWNLOAD_FAILED
        ),
        message="음성 파일을 다운로드하지 못했습니다.",
        retryable=True,
    )


def _validate_content_length(
    *,
    response: httpx.Response,
    max_size_bytes: int,
) -> None:
    value = response.headers.get("content-length")

    if value is None:
        return

    try:
        content_length = int(value)
    except ValueError:
        return

    if content_length > max_size_bytes:
        raise AudioDownloadError(
            reason_code=(
                AudioDownloadReason
                .AUDIO_DOWNLOAD_FAILED
            ),
            message=(
                "음성 파일이 허용된 최대 크기를 "
                "초과했습니다."
            ),
            retryable=False,
        )
