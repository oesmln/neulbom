import React from "react";
import { Platform } from "react-native";
import {
  RecordingPresets,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
  useAudioRecorderState,
} from "expo-audio";

import { newClientId, recordings } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import type { Uuid } from "@/api/types";

type AnswerRecordingTarget = {
  userId: Uuid | null;
  sessionId: Uuid | null;
  questionId: Uuid | null;
};

type CapturedAudio = {
  uri: string;
  clientRecordingId: Uuid;
  recordedAt: string;
  mimeType: string;
  fileName: string;
};

export function useAnswerRecording(target: AnswerRecordingTarget) {
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder, 250);
  const capturedRef = React.useRef<CapturedAudio | null>(null);
  const activeRef = React.useRef(false);
  const [recordingId, setRecordingId] = React.useState<Uuid | null>(null);
  const [uploading, setUploading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    capturedRef.current = null;
    setRecordingId(null);
    setError(null);
  }, [target.sessionId, target.questionId]);

  React.useEffect(
    () => () => {
      if (!activeRef.current) return;
      activeRef.current = false;
      void recorder
        .stop()
        .catch(() => undefined)
        .finally(() => setAudioModeAsync({ allowsRecording: false }).catch(() => undefined));
    },
    [recorder],
  );

  const uploadCaptured = React.useCallback(async () => {
    const captured = capturedRef.current;
    if (!captured || !target.userId || !target.sessionId || !target.questionId) {
      throw new Error("녹음 업로드 정보가 준비되지 않았습니다.");
    }

    setUploading(true);
    setError(null);
    try {
      const response = await recordings.upload({
        ...captured,
        userId: target.userId,
        purpose: "answer",
        sessionId: target.sessionId,
        questionId: target.questionId,
        deviceStatus: "device_saved",
      });
      setRecordingId(response.recording_id);
      return response.recording_id;
    } catch (cause) {
      setError(apiErrorMessage(cause));
      throw cause;
    } finally {
      setUploading(false);
    }
  }, [target.questionId, target.sessionId, target.userId]);

  const start = React.useCallback(async () => {
    if (!target.userId || !target.sessionId || !target.questionId) return;
    setError(null);
    const permission = await requestRecordingPermissionsAsync();
    if (!permission.granted) {
      setError("음성 답변을 저장하려면 마이크 권한이 필요합니다.");
      return;
    }
    await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
    try {
      await recorder.prepareToRecordAsync();
      recorder.record();
      activeRef.current = true;
    } catch (cause) {
      await setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
      throw cause;
    }
  }, [recorder, target.questionId, target.sessionId, target.userId]);

  const stopAndUpload = React.useCallback(async () => {
    try {
      await recorder.stop();
    } finally {
      activeRef.current = false;
      await setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
    }
    if (!recorder.uri) {
      setError("녹음 파일을 만들지 못했습니다. 다시 녹음해 주세요.");
      return null;
    }

    const web = Platform.OS === "web";
    capturedRef.current = {
      uri: recorder.uri,
      clientRecordingId: newClientId(),
      recordedAt: new Date().toISOString(),
      mimeType: web ? "audio/webm" : "audio/mp4",
      fileName: web ? "answer.webm" : "answer.m4a",
    };
    return uploadCaptured();
  }, [recorder, uploadCaptured]);

  const toggle = React.useCallback(async () => {
    if (uploading || recordingId) return null;
    try {
      if (recorderState.isRecording) {
        return await stopAndUpload();
      }
      if (capturedRef.current) {
        return await uploadCaptured();
      }
      await start();
      return null;
    } catch (cause) {
      setError(apiErrorMessage(cause));
      return null;
    }
  }, [recordingId, recorderState.isRecording, start, stopAndUpload, uploadCaptured, uploading]);

  return {
    isRecording: recorderState.isRecording,
    durationMillis: recorderState.durationMillis,
    uploading,
    recordingId,
    error,
    toggle,
  };
}
