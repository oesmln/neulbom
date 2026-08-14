import React from "react";
import { Platform } from "react-native";
import {
  RecordingPresets,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
  useAudioRecorderState,
} from "expo-audio";

import { newClientId } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import type { Uuid } from "@/api/types";
import {
  enqueueRecording,
  consumeUploadedRecording,
  findQueuedRecording,
  subscribeRecordingQueue,
  syncRecordingQueue,
  type RecordingQueueStatus,
} from "@/recording/recordingQueue";

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

export function useAnswerRecording(
  target: AnswerRecordingTarget,
  onUploaded?: (recordingId: Uuid) => void,
  onTranscribed?: (transcript: string, transcriptId?: Uuid) => void,
) {
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder, 250);
  const capturedRef = React.useRef<CapturedAudio | null>(null);
  const queuedClientIdRef = React.useRef<Uuid | null>(null);
  const reportedRecordingIdRef = React.useRef<Uuid | null>(null);
  const onUploadedRef = React.useRef(onUploaded);
  const onTranscribedRef = React.useRef(onTranscribed);
  const activeRef = React.useRef(false);
  const [recordingId, setRecordingId] = React.useState<Uuid | null>(null);
  const [syncStatus, setSyncStatus] = React.useState<RecordingQueueStatus | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  onUploadedRef.current = onUploaded;
  onTranscribedRef.current = onTranscribed;

  const complete = React.useCallback((id: Uuid) => {
    if (reportedRecordingIdRef.current === id) return;
    reportedRecordingIdRef.current = id;
    setRecordingId(id);
    setSyncStatus(null);
    setError(null);
    onUploadedRef.current?.(id);
  }, []);

  React.useEffect(() => {
    capturedRef.current = null;
    queuedClientIdRef.current = null;
    reportedRecordingIdRef.current = null;
    setRecordingId(null);
    setSyncStatus(null);
    setError(null);
    if (!target.userId || !target.sessionId || !target.questionId) return;

    let cancelled = false;
    void (async () => {
      const uploaded = await consumeUploadedRecording(
        target.userId as Uuid,
        target.sessionId as Uuid,
        target.questionId as Uuid,
      );
      if (cancelled) return;
      if (uploaded) {
        complete(uploaded.recordingId);
        if (uploaded.transcript) {
          onTranscribedRef.current?.(uploaded.transcript, uploaded.transcriptId);
        }
        return;
      }
      const item = await findQueuedRecording(
        target.userId as Uuid,
        target.sessionId as Uuid,
        target.questionId as Uuid,
      );
      if (cancelled || !item) return;
      queuedClientIdRef.current = item.clientRecordingId;
      setSyncStatus(item.status);
      setError(item.error ?? "저장된 녹음을 네트워크 연결 후 다시 전송할게요.");
      void syncRecordingQueue(target.userId as Uuid).catch(() => undefined);
    })().catch((cause) => {
      if (!cancelled) setError(apiErrorMessage(cause));
    });
    return () => {
      cancelled = true;
    };
  }, [complete, target.questionId, target.sessionId, target.userId]);

  React.useEffect(
    () =>
      subscribeRecordingQueue((event) => {
        if (event.type === "changed" && event.item.clientRecordingId === queuedClientIdRef.current) {
          setSyncStatus(event.item.status);
          setError(
            event.item.status === "failed"
              ? event.item.error ?? "녹음을 전송하지 못했어요. 다시 시도해 주세요."
              : event.item.status === "pending"
                ? "녹음이 기기에 안전하게 저장됐어요. 연결되면 자동 전송할게요."
                : null,
          );
        }
        if (event.type === "uploaded" && event.clientRecordingId === queuedClientIdRef.current) {
          queuedClientIdRef.current = null;
          complete(event.recordingId);
          if (event.transcript) {
            onTranscribedRef.current?.(event.transcript, event.transcriptId);
          }
          void consumeUploadedRecording(
            target.userId as Uuid,
            target.sessionId as Uuid,
            target.questionId as Uuid,
          );
        }
        if (event.type === "removed" && event.clientRecordingId === queuedClientIdRef.current) {
          queuedClientIdRef.current = null;
          setSyncStatus(null);
        }
      }),
    [complete, target.questionId, target.sessionId, target.userId],
  );

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

    setError(null);
    try {
      const queued = await enqueueRecording({
        ...captured,
        userId: target.userId,
        purpose: "answer",
        sessionId: target.sessionId,
        questionId: target.questionId,
      });
      queuedClientIdRef.current = queued.clientRecordingId;
      setSyncStatus("pending");
      await syncRecordingQueue(target.userId);
    } catch (cause) {
      setError(apiErrorMessage(cause));
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
    await uploadCaptured();
  }, [recorder, uploadCaptured]);

  const toggle = React.useCallback(async () => {
    if (syncStatus === "uploading" || recordingId) return;
    try {
      if (recorderState.isRecording) {
        await stopAndUpload();
        return;
      }
      if (queuedClientIdRef.current && target.userId) {
        await syncRecordingQueue(target.userId);
        return;
      }
      await start();
    } catch (cause) {
      setError(apiErrorMessage(cause));
    }
  }, [recordingId, recorderState.isRecording, start, stopAndUpload, syncStatus, target.userId]);

  return {
    isRecording: recorderState.isRecording,
    durationMillis: recorderState.durationMillis,
    uploading: syncStatus === "uploading",
    syncStatus,
    recordingId,
    error,
    toggle,
  };
}
