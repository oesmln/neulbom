import { AppState, Platform } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { Directory, File, Paths } from "expo-file-system";

import { recordings } from "@/api";
import { USE_MOCK_API } from "@/api/config";
import { ApiError, apiErrorMessage } from "@/api/errors";
import { currentSession } from "@/api/tokens";
import type { RecordingPurpose, Uuid } from "@/api/types";

export type RecordingQueueStatus = "pending" | "uploading" | "failed";

export type RecordingQueueItem = {
  clientRecordingId: Uuid;
  userId: Uuid;
  purpose: RecordingPurpose;
  sessionId?: Uuid;
  questionId?: Uuid;
  recordedAt: string;
  mimeType: string;
  fileName: string;
  localUri: string;
  status: RecordingQueueStatus;
  attempts: number;
  createdAt: string;
  lastAttemptAt?: string;
  error?: string;
};

export type RecordingQueueEvent =
  | { type: "changed"; item: RecordingQueueItem }
  | { type: "uploaded"; clientRecordingId: Uuid; recordingId: Uuid }
  | { type: "removed"; clientRecordingId: Uuid };

type CapturedRecording = Omit<
  RecordingQueueItem,
  "localUri" | "status" | "attempts" | "createdAt" | "lastAttemptAt" | "error"
> & { uri: string };

type WebQueueRow = RecordingQueueItem & { audio: Blob };
type RecordingReceipt = {
  clientRecordingId: Uuid;
  userId: Uuid;
  sessionId?: Uuid;
  questionId?: Uuid;
  recordingId: Uuid;
  completedAt: string;
};
type WebStoredRow =
  | (WebQueueRow & { recordKind: "queue" })
  | (RecordingReceipt & { recordKind: "receipt" });

const QUEUE_DIRECTORY = "recording-upload-queue";
const QUEUE_METADATA = "queue.json";
const RECEIPT_METADATA = "receipts.json";
const WEB_DB = "neulbom-recording-queue";
const WEB_STORE = "recordings";
const MAX_QUEUE_AGE_MS = 7 * 24 * 60 * 60 * 1000;

const listeners = new Set<(event: RecordingQueueEvent) => void>();
let syncInFlight: Promise<void> | null = null;
let requestedSyncUserId: Uuid | null = null;
let mutationChain: Promise<unknown> = Promise.resolve();

function emit(event: RecordingQueueEvent) {
  for (const listener of listeners) listener(event);
}

export function subscribeRecordingQueue(listener: (event: RecordingQueueEvent) => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function serializeMutation<T>(operation: () => Promise<T>): Promise<T> {
  const next = mutationChain.then(operation, operation);
  mutationChain = next.catch(() => undefined);
  return next;
}

function nativeDirectory() {
  const directory = new Directory(Paths.document, QUEUE_DIRECTORY);
  directory.create({ idempotent: true, intermediates: true });
  return directory;
}

async function readNativeQueue(): Promise<RecordingQueueItem[]> {
  const metadata = new File(nativeDirectory(), QUEUE_METADATA);
  if (!metadata.exists) return [];
  try {
    const value = JSON.parse(await metadata.text()) as unknown;
    return Array.isArray(value) ? (value as RecordingQueueItem[]) : [];
  } catch {
    // Corrupt metadata must not make the app crash or upload an unknown file.
    const directory = nativeDirectory();
    directory.delete();
    directory.create({ idempotent: true, intermediates: true });
    return [];
  }
}

async function writeNativeQueue(items: RecordingQueueItem[]): Promise<void> {
  const directory = nativeDirectory();
  const temporary = new File(directory, `${QUEUE_METADATA}.tmp`);
  temporary.create({ overwrite: true, intermediates: true });
  temporary.write(JSON.stringify(items));
  await temporary.move(new File(directory, QUEUE_METADATA), { overwrite: true });
}

function openWebDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(WEB_DB, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(WEB_STORE)) {
        request.result.createObjectStore(WEB_STORE, { keyPath: "clientRecordingId" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("녹음 저장소를 열지 못했습니다."));
  });
}

async function webRequest<T>(mode: IDBTransactionMode, run: (store: IDBObjectStore) => IDBRequest<T>) {
  const database = await openWebDatabase();
  try {
    return await new Promise<T>((resolve, reject) => {
      const transaction = database.transaction(WEB_STORE, mode);
      const request = run(transaction.objectStore(WEB_STORE));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error ?? new Error("녹음 저장소 요청에 실패했습니다."));
    });
  } finally {
    database.close();
  }
}

async function listStored(): Promise<RecordingQueueItem[]> {
  if (Platform.OS === "web") {
    const rows = await webRequest<WebStoredRow[]>("readonly", (store) => store.getAll());
    return rows
      .filter((row): row is WebQueueRow & { recordKind: "queue" } => row.recordKind === "queue")
      .map(({ audio: _audio, recordKind: _recordKind, ...item }) => item);
  }
  return readNativeQueue();
}

async function replaceNativeItem(item: RecordingQueueItem): Promise<void> {
  const items = await readNativeQueue();
  const index = items.findIndex((candidate) => candidate.clientRecordingId === item.clientRecordingId);
  if (index >= 0) items[index] = item;
  else items.push(item);
  await writeNativeQueue(items);
}

async function putStored(item: RecordingQueueItem, audio?: Blob): Promise<void> {
  if (Platform.OS === "web") {
    const previous = await webRequest<WebStoredRow | undefined>("readonly", (store) =>
      store.get(item.clientRecordingId),
    );
    const persistedAudio =
      audio ?? (previous?.recordKind === "queue" ? previous.audio : undefined);
    if (!persistedAudio) throw new Error("저장된 녹음 파일을 찾지 못했습니다.");
    await webRequest<IDBValidKey>("readwrite", (store) =>
      store.put({ ...item, audio: persistedAudio, recordKind: "queue" }),
    );
    return;
  }
  await replaceNativeItem(item);
}

async function deleteStored(clientRecordingId: Uuid): Promise<void> {
  if (Platform.OS === "web") {
    await webRequest<undefined>("readwrite", (store) => store.delete(clientRecordingId));
    return;
  }
  const items = await readNativeQueue();
  const item = items.find((candidate) => candidate.clientRecordingId === clientRecordingId);
  if (item) {
    const audio = new File(item.localUri);
    if (audio.exists) audio.delete();
  }
  await writeNativeQueue(items.filter((candidate) => candidate.clientRecordingId !== clientRecordingId));
}

async function uploadUri(item: RecordingQueueItem): Promise<{ uri: string; release: () => void }> {
  if (Platform.OS !== "web") return { uri: item.localUri, release: () => undefined };
  const row = await webRequest<WebStoredRow | undefined>("readonly", (store) =>
    store.get(item.clientRecordingId),
  );
  if (!row || row.recordKind !== "queue" || !row.audio) {
    throw new Error("저장된 녹음 파일을 찾지 못했습니다.");
  }
  const uri = URL.createObjectURL(row.audio);
  return { uri, release: () => URL.revokeObjectURL(uri) };
}

export async function enqueueRecording(input: CapturedRecording): Promise<RecordingQueueItem> {
  return serializeMutation(async () => {
    let localUri = `indexeddb://${input.clientRecordingId}`;
    let webAudio: Blob | undefined;
    if (Platform.OS === "web") {
      webAudio = await (await fetch(input.uri)).blob();
    } else {
      const extension = input.fileName.split(".").pop() || "m4a";
      const destination = new File(nativeDirectory(), `${input.clientRecordingId}.${extension}`);
      await new File(input.uri).copy(destination, { overwrite: true });
      localUri = destination.uri;
    }

    const item: RecordingQueueItem = {
      clientRecordingId: input.clientRecordingId,
      userId: input.userId,
      purpose: input.purpose,
      sessionId: input.sessionId,
      questionId: input.questionId,
      recordedAt: input.recordedAt,
      mimeType: input.mimeType,
      fileName: input.fileName,
      localUri,
      status: "pending",
      attempts: 0,
      createdAt: new Date().toISOString(),
    };
    await putStored(item, webAudio);
    emit({ type: "changed", item });
    return item;
  });
}

export async function findQueuedRecording(
  userId: Uuid,
  sessionId: Uuid,
  questionId: Uuid,
): Promise<RecordingQueueItem | null> {
  const items = await listStored();
  return (
    items.find(
      (item) =>
        item.userId === userId && item.sessionId === sessionId && item.questionId === questionId,
    ) ?? null
  );
}

async function readReceipts(): Promise<RecordingReceipt[]> {
  if (Platform.OS === "web") {
    const rows = await webRequest<WebStoredRow[]>("readonly", (store) => store.getAll());
    return rows
      .filter(
        (row): row is RecordingReceipt & { recordKind: "receipt" } =>
          row.recordKind === "receipt",
      )
      .map(({ recordKind: _recordKind, ...receipt }) => receipt);
  }
  const file = new File(nativeDirectory(), RECEIPT_METADATA);
  if (!file.exists) return [];
  try {
    const value = JSON.parse(await file.text()) as unknown;
    return Array.isArray(value) ? (value as RecordingReceipt[]) : [];
  } catch {
    file.delete();
    return [];
  }
}

async function writeReceipts(receipts: RecordingReceipt[]): Promise<void> {
  if (Platform.OS === "web") return;
  const file = new File(nativeDirectory(), RECEIPT_METADATA);
  file.create({ overwrite: true, intermediates: true });
  file.write(JSON.stringify(receipts));
}

async function saveReceipt(receipt: RecordingReceipt): Promise<void> {
  if (Platform.OS === "web") {
    await webRequest<IDBValidKey>("readwrite", (store) =>
      store.put({ ...receipt, recordKind: "receipt" }),
    );
    return;
  }
  const receipts = await readReceipts();
  await writeReceipts([
    ...receipts.filter((item) => item.clientRecordingId !== receipt.clientRecordingId),
    receipt,
  ]);
}

async function deleteReceipt(clientRecordingId: Uuid): Promise<void> {
  if (Platform.OS === "web") {
    await webRequest<undefined>("readwrite", (store) => store.delete(clientRecordingId));
    return;
  }
  const receipts = await readReceipts();
  await writeReceipts(
    receipts.filter((receipt) => receipt.clientRecordingId !== clientRecordingId),
  );
}

/** Consume the small upload receipt left when background sync finished off-screen. */
export async function consumeUploadedRecording(
  userId: Uuid,
  sessionId: Uuid,
  questionId: Uuid,
): Promise<Uuid | null> {
  return serializeMutation(async () => {
    const receipts = await readReceipts();
    const receipt = receipts.find(
      (item) =>
        item.userId === userId && item.sessionId === sessionId && item.questionId === questionId,
    );
    if (!receipt) return null;
    await deleteReceipt(receipt.clientRecordingId);
    return receipt.recordingId;
  });
}

async function cleanupQueue(activeUserId: Uuid): Promise<void> {
  const now = Date.now();
  const items = await listStored();
  for (const item of items) {
    const expired = now - Date.parse(item.createdAt) > MAX_QUEUE_AGE_MS;
    if (expired || item.userId !== activeUserId) {
      await deleteStored(item.clientRecordingId);
      emit({ type: "removed", clientRecordingId: item.clientRecordingId });
    }
  }
  const receipts = await readReceipts();
  for (const receipt of receipts) {
    const expired = now - Date.parse(receipt.completedAt) > MAX_QUEUE_AGE_MS;
    if (expired || receipt.userId !== activeUserId) {
      await deleteReceipt(receipt.clientRecordingId);
    }
  }
}

async function performSync(activeUserId: Uuid): Promise<void> {
  await serializeMutation(() => cleanupQueue(activeUserId));
  const items = (await listStored()).filter((item) => item.userId === activeUserId);
  for (const item of items) {
    if (!USE_MOCK_API && currentSession()?.userId !== activeUserId) break;
    const uploading: RecordingQueueItem = {
      ...item,
      status: "uploading",
      attempts: item.attempts + 1,
      lastAttemptAt: new Date().toISOString(),
      error: undefined,
    };
    await serializeMutation(() => putStored(uploading));
    emit({ type: "changed", item: uploading });

    let releaseSource: () => void = () => undefined;
    try {
      const source = await uploadUri(uploading);
      releaseSource = source.release;
      const response = await recordings.upload({
        uri: source.uri,
        clientRecordingId: uploading.clientRecordingId,
        userId: uploading.userId,
        purpose: uploading.purpose,
        sessionId: uploading.sessionId,
        questionId: uploading.questionId,
        recordedAt: uploading.recordedAt,
        deviceStatus: "device_saved",
        mimeType: uploading.mimeType,
        fileName: uploading.fileName,
      });
      await serializeMutation(() => deleteStored(uploading.clientRecordingId));
      await serializeMutation(() =>
        saveReceipt({
          clientRecordingId: uploading.clientRecordingId,
          userId: uploading.userId,
          sessionId: uploading.sessionId,
          questionId: uploading.questionId,
          recordingId: response.recording_id,
          completedAt: new Date().toISOString(),
        }),
      );
      emit({
        type: "uploaded",
        clientRecordingId: uploading.clientRecordingId,
        recordingId: response.recording_id,
      });
    } catch (cause) {
      const failed: RecordingQueueItem = {
        ...uploading,
        status: "failed",
        error: apiErrorMessage(cause),
      };
      await serializeMutation(() => putStored(failed));
      emit({ type: "changed", item: failed });
      if (cause instanceof ApiError && cause.isUnauthorized) break;
    } finally {
      releaseSource();
    }
  }
}

export function syncRecordingQueue(activeUserId: Uuid): Promise<void> {
  requestedSyncUserId = activeUserId;
  if (syncInFlight) return syncInFlight;
  syncInFlight = (async () => {
    while (requestedSyncUserId) {
      const nextUserId = requestedSyncUserId;
      requestedSyncUserId = null;
      await performSync(nextUserId);
    }
  })().finally(() => {
    syncInFlight = null;
  });
  return syncInFlight;
}

/** Sync after session restore, app foregrounding, and an offline → online transition. */
export function installRecordingQueueSync(activeUserId: Uuid): () => void {
  void syncRecordingQueue(activeUserId).catch(() => undefined);
  const networkSubscription = NetInfo.addEventListener((state) => {
    if (state.isConnected && state.isInternetReachable !== false) {
      void syncRecordingQueue(activeUserId).catch(() => undefined);
    }
  });
  const appSubscription = AppState.addEventListener("change", (state) => {
    if (state === "active") void syncRecordingQueue(activeUserId).catch(() => undefined);
  });
  return () => {
    networkSubscription();
    appSubscription.remove();
  };
}
