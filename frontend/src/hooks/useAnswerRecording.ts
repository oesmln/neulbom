import React from "react";
import { Platform } from "react-native";
import {
  AudioQuality,
  IOSOutputFormat,
  RecordingPresets,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioPlayer,
  useAudioRecorder,
  useAudioRecorderState,
  type RecordingOptions,
} from "expo-audio";

import { newClientId } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import type { Uuid } from "@/api/types";
import {
  discardQueuedRecording,
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
  durationMs: number;
};

export const MAX_ANSWER_RECORDING_DURATION_MS = 60_000;

/**
 * 답변 녹음은 듣기용이 아니라 음성 인식용이다. `RecordingPresets.HIGH_QUALITY`는
 * 44.1kHz 스테레오로 담아 같은 길이라도 파일이 두 배 이상 커진다. 인식이 기대하는
 * 모노 16kHz로 맞추면 업로드가 빨라지고 전사도 안정적이다.
 */
const ANSWER_RECORDING_OPTIONS: RecordingOptions = {
  ...RecordingPresets.HIGH_QUALITY,
  sampleRate: 16_000,
  numberOfChannels: 1,
  bitRate: 64_000,
  android: { outputFormat: "mpeg4", audioEncoder: "aac" },
  ios: {
    outputFormat: IOSOutputFormat.MPEG4AAC,
    audioQuality: AudioQuality.HIGH,
    linearPCMBitDepth: 16,
    linearPCMIsBigEndian: false,
    linearPCMIsFloat: false,
  },
  web: { mimeType: "audio/webm", bitsPerSecond: 64_000 },
};

export function useAnswerRecording(
  target: AnswerRecordingTarget,
  onUploaded?: (recordingId: Uuid) => void,
  onTranscribed?: (transcript: string, transcriptId?: Uuid) => void,
) {
  const recorder = useAudioRecorder(ANSWER_RECORDING_OPTIONS);
  // 녹음 시작·종료를 귀로 확인할 수 있는 짧은 알림음. 고령 사용자가 녹음이 안 된
  // 상태로 말하는 것을 막는다. 시작음은 재생을 마친 뒤 녹음을 열어 효과음이 답변에
  // 섞이지 않게 한다.
  const startBeep = useAudioPlayer(require("../../assets/sounds/record-start.wav"));
  const stopBeep = useAudioPlayer(require("../../assets/sounds/record-stop.wav"));
  const recorderState = useAudioRecorderState(recorder, 250);
  const capturedRef = React.useRef<CapturedAudio | null>(null);
  const queuedClientIdRef = React.useRef<Uuid | null>(null);
  const reportedRecordingIdRef = React.useRef<Uuid | null>(null);
  const onUploadedRef = React.useRef(onUploaded);
  const onTranscribedRef = React.useRef(onTranscribed);
  const activeRef = React.useRef(false);
  const stoppingRef = React.useRef(false);
  const autoStopTimerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);
  const stopAndUploadRef = React.useRef<(atLimit?: boolean) => Promise<void>>(async () => undefined);
  const [recordingId, setRecordingId] = React.useState<Uuid | null>(null);
  const [syncStatus, setSyncStatus] = React.useState<RecordingQueueStatus | null>(null);
  // 실패한 전송을 다시 올려볼 수 있는지. `false`면 버튼은 새 녹음을 시작한다.
  const [canResend, setCanResend] = React.useState(true);
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
    setCanResend(true);
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
        if (uploaded.transcript) {
          onTranscribedRef.current?.(uploaded.transcript, uploaded.transcriptId);
        }
        complete(uploaded.recordingId);
        return;
      }
      const item = await findQueuedRecording(
        target.userId as Uuid,
        target.sessionId as Uuid,
        target.questionId as Uuid,
      );
      if (cancelled || !item) return;
      const resendable = item.retryable !== false;
      queuedClientIdRef.current = item.clientRecordingId;
      setSyncStatus(item.status);
      setCanResend(resendable);
      setError(item.error ?? "저장된 녹음을 네트워크 연결 후 다시 전송할게요.");
      // 다시 올려도 같은 결과인 녹음은 화면에 들어올 때 자동으로 재전송하지 않는다.
      if (resendable) void syncRecordingQueue(target.userId as Uuid).catch(() => undefined);
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
          setCanResend(event.item.retryable !== false);
          setError(
            event.item.status === "failed"
              ? event.item.error ?? "녹음을 전송하지 못했어요. 다시 녹음해 주세요."
              : event.item.status === "pending"
                ? "녹음이 기기에 안전하게 저장됐어요. 연결되면 자동 전송할게요."
                : null,
          );
        }
        if (event.type === "uploaded" && event.clientRecordingId === queuedClientIdRef.current) {
          queuedClientIdRef.current = null;
          if (event.transcript) {
            onTranscribedRef.current?.(event.transcript, event.transcriptId);
          }
          complete(event.recordingId);
          void consumeUploadedRecording(
            target.userId as Uuid,
            target.sessionId as Uuid,
            target.questionId as Uuid,
          );
        }
        if (event.type === "removed" && event.clientRecordingId === queuedClientIdRef.current) {
          queuedClientIdRef.current = null;
          setSyncStatus(null);
          setCanResend(true);
        }
      }),
    [complete, target.questionId, target.sessionId, target.userId],
  );

  React.useEffect(
    () => () => {
      if (autoStopTimerRef.current) clearTimeout(autoStopTimerRef.current);
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
      setCanResend(true);
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
    try {
      await startBeep.seekTo(0);
      startBeep.play();
      await new Promise((resolve) => setTimeout(resolve, 260));
    } catch {
      // 알림음 실패가 녹음을 막아서는 안 된다.
    }
    await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
    try {
      await recorder.prepareToRecordAsync();
      recorder.record();
      activeRef.current = true;
      autoStopTimerRef.current = setTimeout(() => {
        void stopAndUploadRef.current(true);
      }, MAX_ANSWER_RECORDING_DURATION_MS);
    } catch (cause) {
      await setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
      throw cause;
    }
  }, [recorder, startBeep, target.questionId, target.sessionId, target.userId]);

  const stopAndUpload = React.useCallback(async (atLimit = false) => {
    if (stoppingRef.current) return;
    stoppingRef.current = true;
    if (autoStopTimerRef.current) {
      clearTimeout(autoStopTimerRef.current);
      autoStopTimerRef.current = null;
    }
    const durationMs = atLimit
      ? MAX_ANSWER_RECORDING_DURATION_MS
      : Math.round(recorderState.durationMillis);
    try {
      await recorder.stop();
    } finally {
      activeRef.current = false;
      await setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
      stoppingRef.current = false;
      try {
        await stopBeep.seekTo(0);
        stopBeep.play();
      } catch {
        // 알림음 실패는 무시한다.
      }
    }
    if (durationMs > MAX_ANSWER_RECORDING_DURATION_MS) {
      setError("답변 녹음은 최대 60초까지 가능합니다. 다시 녹음해 주세요.");
      return;
    }
    if (!recorder.uri) {
      setError("녹음 파일을 만들지 못했습니다. 다시 녹음해 주세요.");
      return;
    }

    const web = Platform.OS === "web";
    capturedRef.current = {
      uri: recorder.uri,
      clientRecordingId: newClientId(),
      recordedAt: new Date().toISOString(),
      mimeType: web ? "audio/webm" : "audio/mp4",
      fileName: web ? "answer.webm" : "answer.m4a",
      durationMs,
    };
    await uploadCaptured();
  }, [recorder, recorderState.durationMillis, stopBeep, uploadCaptured]);

  stopAndUploadRef.current = stopAndUpload;

  const toggle = React.useCallback(async () => {
    if (syncStatus === "uploading" || recordingId) return;
    try {
      if (recorderState.isRecording) {
        await stopAndUpload();
        return;
      }
      if (syncStatus === "failed" && !canResend) {
        // 같은 파일을 또 올리는 대신, 화면 안내대로 새 답변을 받는다. 버리지
        // 않으면 이 문항은 실패한 녹음에 묶여 더 진행할 수 없다.
        const discarded = queuedClientIdRef.current;
        queuedClientIdRef.current = null;
        setSyncStatus(null);
        setCanResend(true);
        setError(null);
        if (discarded) await discardQueuedRecording(discarded);
      } else if (queuedClientIdRef.current && target.userId) {
        await syncRecordingQueue(target.userId);
        return;
      }
      await start();
    } catch (cause) {
      setError(apiErrorMessage(cause));
    }
  }, [canResend, recordingId, recorderState.isRecording, start, stopAndUpload, syncStatus, target.userId]);

  return {
    isRecording: recorderState.isRecording,
    durationMillis: recorderState.durationMillis,
    uploading: syncStatus === "uploading",
    syncStatus,
    canResend,
    recordingId,
    error,
    toggle,
  };
}
