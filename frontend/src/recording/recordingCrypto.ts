import { AESEncryptionKey, AESSealedData, aesDecryptAsync, aesEncryptAsync } from "expo-crypto";
import * as SecureStore from "expo-secure-store";

import type { Uuid } from "@/api/types";

const NATIVE_KEY_PREFIX = "neulbom.recording.queue.key.";
const WEB_KEY_DB = "neulbom-recording-keys";
const WEB_KEY_STORE = "keys";
const NONCE_BYTES = 12;

async function nativeKey(userId: Uuid): Promise<AESEncryptionKey> {
  const name = `${NATIVE_KEY_PREFIX}${userId}`;
  const saved = await SecureStore.getItemAsync(name);
  if (saved) return AESEncryptionKey.import(saved, "base64");

  const generated = await AESEncryptionKey.generate();
  await SecureStore.setItemAsync(name, await generated.encoded("base64"));
  return generated;
}

export async function encryptNativeAudio(userId: Uuid, plaintext: Uint8Array): Promise<Uint8Array> {
  const sealed = await aesEncryptAsync(plaintext, await nativeKey(userId));
  return sealed.combined();
}

export async function decryptNativeAudio(userId: Uuid, encrypted: Uint8Array): Promise<Uint8Array> {
  return aesDecryptAsync(AESSealedData.fromCombined(encrypted), await nativeKey(userId));
}

function openWebKeyDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(WEB_KEY_DB, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(WEB_KEY_STORE)) {
        request.result.createObjectStore(WEB_KEY_STORE);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("녹음 암호화 키 저장소를 열지 못했습니다."));
  });
}

function webKeyRequest<T>(
  database: IDBDatabase,
  mode: IDBTransactionMode,
  run: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(WEB_KEY_STORE, mode);
    const request = run(transaction.objectStore(WEB_KEY_STORE));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("녹음 암호화 키를 처리하지 못했습니다."));
  });
}

async function webKey(userId: Uuid): Promise<CryptoKey> {
  const database = await openWebKeyDatabase();
  try {
    const saved = await webKeyRequest<CryptoKey | undefined>(database, "readonly", (store) => store.get(userId));
    if (saved) return saved;
    const generated = await crypto.subtle.generateKey(
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"],
    );
    await webKeyRequest<IDBValidKey>(database, "readwrite", (store) => store.put(generated, userId));
    return generated;
  } finally {
    database.close();
  }
}

export async function encryptWebAudio(userId: Uuid, audio: Blob): Promise<Blob> {
  const nonce = crypto.getRandomValues(new Uint8Array(NONCE_BYTES));
  const plaintext = await audio.arrayBuffer();
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: nonce },
    await webKey(userId),
    plaintext,
  ));
  const output = new Uint8Array(nonce.length + ciphertext.length);
  output.set(nonce);
  output.set(ciphertext, nonce.length);
  return new Blob([output], { type: "application/octet-stream" });
}

export async function decryptWebAudio(userId: Uuid, encrypted: Blob, mimeType: string): Promise<Blob> {
  const content = new Uint8Array(await encrypted.arrayBuffer());
  if (content.length <= NONCE_BYTES + 16) {
    throw new Error("암호화된 녹음 파일이 손상되었습니다.");
  }
  const nonce = content.slice(0, NONCE_BYTES);
  const ciphertext = content.slice(NONCE_BYTES);
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: nonce },
    await webKey(userId),
    ciphertext,
  );
  return new Blob([plaintext], { type: mimeType });
}
