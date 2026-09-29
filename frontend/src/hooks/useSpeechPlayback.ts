import React from "react";
import { Platform } from "react-native";
import {
  setAudioModeAsync,
  useAudioPlayer,
  useAudioPlayerStatus,
} from "expo-audio";
import { EncodingType, File, Paths } from "expo-file-system";
import * as Crypto from "expo-crypto";

import { speech } from "@/api";
import { apiErrorMessage } from "@/api/errors";

type TemporaryAudio = {
  uri: string;
  file: File | null;
};

function audioSource(base64: string, contentType: string): TemporaryAudio {
  if (Platform.OS === "web") {
    return { uri: `data:${contentType};base64,${base64}`, file: null };
  }

  const extension = contentType === "audio/wav" ? "wav" : "mp3";
  const file = new File(Paths.cache, `neulbom-tts-${Crypto.randomUUID()}.${extension}`);
  file.create({ overwrite: true });
  file.write(base64, { encoding: EncodingType.Base64 });
  return { uri: file.uri, file };
}

/**
 * expo-audio releases the shared player object when the owning screen
 * unmounts, and a `pause()` that races that release throws ("Cannot use
 * shared object that was already released") instead of no-opping. That error
 * escapes React's render phase and blanks the whole tree, so every pause on
 * a possibly-released player goes through here.
 */
function pauseQuietly(player: { pause: () => void }) {
  try {
    player.pause();
  } catch {
    // Already released — nothing left to pause.
  }
}

function removeFile(file: File | null) {
  if (!file?.exists) return;
  try {
    file.delete();
  } catch {
    // The cache directory is disposable; a later OS cleanup can remove it.
  }
}

/**
 * expo-audio reports a failed decode/playback (e.g. the browser's
 * `MEDIA_ERR_DECODE`) asynchronously through the player's `status.error`,
 * not as a thrown exception from `replace()`/`play()`, so `play()`'s own
 * `try/catch` never sees it. Elderly users would otherwise see the raw
 * native message (e.g. "Playback error (code 3)") with no way to recover
 * beyond a long press, so a failed attempt is retried once automatically
 * before falling back to a Korean message.
 */
const PLAYBACK_RETRY_ERROR = "음성 안내를 재생하지 못했어요. 다시 듣기 버튼을 눌러 주세요.";

/** Plays one character line through the authenticated Google TTS endpoint. */
export function useSpeechPlayback(line: string | null) {
  const player = useAudioPlayer(null, { updateInterval: 100 });
  const status = useAudioPlayerStatus(player);
  const [enabled, setEnabled] = React.useState(true);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [playbackFailed, setPlaybackFailed] = React.useState(false);
  const requestSequence = React.useRef(0);
  const temporaryFile = React.useRef<File | null>(null);
  const attempt = React.useRef<{ text: string; retried: boolean } | null>(null);

  const stop = React.useCallback(() => {
    requestSequence.current += 1;
    attempt.current = null;
    pauseQuietly(player);
    void player.seekTo(0).catch(() => undefined);
    setLoading(false);
    setPlaybackFailed(false);
  }, [player]);

  const play = React.useCallback(async (text: string, isRetry = false) => {
    const sequence = ++requestSequence.current;
    if (!isRetry) attempt.current = { text, retried: false };
    pauseQuietly(player);
    setLoading(true);
    setError(null);
    setPlaybackFailed(false);
    try {
      const response = await speech.synthesize({ text });
      if (sequence !== requestSequence.current) return;
      const source = audioSource(response.audio_content_base64, response.content_type);
      removeFile(temporaryFile.current);
      temporaryFile.current = source.file;
      await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true });
      player.replace({ uri: source.uri, name: "메모이 안내 음성" });
      player.play();
    } catch (cause) {
      if (sequence === requestSequence.current) {
        setError(apiErrorMessage(cause));
      }
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [player]);

  // Native playback failures surface here, after `play()` has already
  // resolved successfully — see the comment on PLAYBACK_RETRY_ERROR.
  React.useEffect(() => {
    if (!status.error || !attempt.current) return;
    if (attempt.current.retried) {
      setPlaybackFailed(true);
      return;
    }
    attempt.current.retried = true;
    void play(attempt.current.text, true);
  }, [status.error, play]);

  React.useEffect(() => {
    if (!enabled || !line?.trim()) {
      stop();
      return;
    }
    void play(line.trim());
  }, [enabled, line, play, stop]);

  React.useEffect(() => () => {
    requestSequence.current += 1;
    attempt.current = null;
    pauseQuietly(player);
    removeFile(temporaryFile.current);
  }, [player]);

  const toggle = React.useCallback(() => {
    setEnabled((current) => !current);
  }, []);

  return {
    enabled,
    loading,
    speaking: enabled && status.playing,
    error: playbackFailed ? PLAYBACK_RETRY_ERROR : error,
    toggle,
    replay: () => {
      if (line?.trim()) void play(line.trim());
    },
  };
}
