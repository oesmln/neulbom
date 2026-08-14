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

function removeFile(file: File | null) {
  if (!file?.exists) return;
  try {
    file.delete();
  } catch {
    // The cache directory is disposable; a later OS cleanup can remove it.
  }
}

/** Plays one character line through the authenticated Google TTS endpoint. */
export function useSpeechPlayback(line: string | null) {
  const player = useAudioPlayer(null, { updateInterval: 100 });
  const status = useAudioPlayerStatus(player);
  const [enabled, setEnabled] = React.useState(true);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const requestSequence = React.useRef(0);
  const temporaryFile = React.useRef<File | null>(null);

  const stop = React.useCallback(() => {
    requestSequence.current += 1;
    player.pause();
    void player.seekTo(0).catch(() => undefined);
    setLoading(false);
  }, [player]);

  const play = React.useCallback(async (text: string) => {
    const sequence = ++requestSequence.current;
    player.pause();
    setLoading(true);
    setError(null);
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

  React.useEffect(() => {
    if (!enabled || !line?.trim()) {
      stop();
      return;
    }
    void play(line.trim());
  }, [enabled, line, play, stop]);

  React.useEffect(() => () => {
    requestSequence.current += 1;
    player.pause();
    removeFile(temporaryFile.current);
  }, [player]);

  const toggle = React.useCallback(() => {
    setEnabled((current) => !current);
  }, []);

  return {
    enabled,
    loading,
    speaking: enabled && status.playing,
    error: status.error ?? error,
    toggle,
    replay: () => {
      if (line?.trim()) void play(line.trim());
    },
  };
}
