/**
 * WebCrypto Envelope Encryption (AES-256-GCM + PBKDF2-SHA256)
 * Mirrors the cryptographic architecture of AtomicVault:
 * - KEK (Key Encryption Key) derived from master password + salt
 * - DEK (Data Encryption Key) 256-bit AES key stored wrapped
 * - Sealed payload: IV (12 bytes) + ciphertext + tag
 * - Backup encoding with 'ATVB' magic header matching BackupCodec
 */

export interface VaultEnvelope {
  saltBase64: string;
  wrappedDek: string; // Base64 [12-byte IV + ciphertext]
  kdfParams: {
    algorithm: string;
    iterations: number;
    hash: string;
  };
}

const KDF_ITERATIONS = 100000;
const BACKUP_MAGIC = new Uint8Array([0x41, 0x54, 0x56, 0x42]); // "ATVB"

export class VaultCrypto {
  /**
   * Derives a KEK from password and Base64 salt using PBKDF2
   */
  static async deriveKek(password: string, saltBytes: Uint8Array): Promise<CryptoKey> {
    const enc = new TextEncoder();
    const passKey = await crypto.subtle.importKey(
      'raw',
      enc.encode(password),
      { name: 'PBKDF2' },
      false,
      ['deriveKey']
    );

    return crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: saltBytes as BufferSource,
        iterations: KDF_ITERATIONS,
        hash: 'SHA-256',
      },
      passKey,
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt', 'wrapKey', 'unwrapKey']
    );
  }

  /**
   * Generates a 256-bit DEK
   */
  static async generateDek(): Promise<CryptoKey> {
    return crypto.subtle.generateKey(
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt']
    );
  }

  /**
   * Wraps DEK using KEK
   */
  static async wrapDek(kek: CryptoKey, dek: CryptoKey): Promise<string> {
    const rawDek = await crypto.subtle.exportKey('raw', dek);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encrypted = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      kek,
      rawDek
    );

    const combined = new Uint8Array(iv.length + encrypted.byteLength);
    combined.set(iv, 0);
    combined.set(new Uint8Array(encrypted), iv.length);
    return this.bytesToBase64(combined);
  }

  /**
   * Unwraps DEK using KEK
   */
  static async unwrapDek(kek: CryptoKey, wrappedDekBase64: string): Promise<CryptoKey> {
    const combined = this.base64ToBytes(wrappedDekBase64);
    if (combined.length < 12 + 16) {
      throw new Error('Invalid wrapped DEK length');
    }
    const iv = combined.slice(0, 12);
    const ciphertext = combined.slice(12);

    const rawDek = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      kek,
      ciphertext
    );

    return crypto.subtle.importKey(
      'raw',
      rawDek,
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt']
    );
  }

  /**
   * Encrypts plaintext string using DEK
   */
  static async seal(dek: CryptoKey, plaintext: string): Promise<string> {
    const enc = new TextEncoder();
    const data = enc.encode(plaintext);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encrypted = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      dek,
      data
    );

    const combined = new Uint8Array(iv.length + encrypted.byteLength);
    combined.set(iv, 0);
    combined.set(new Uint8Array(encrypted), iv.length);
    return this.bytesToBase64(combined);
  }

  /**
   * Decrypts ciphertext string using DEK
   */
  static async open(dek: CryptoKey, ciphertextBase64: string): Promise<string> {
    const combined = this.base64ToBytes(ciphertextBase64);
    if (combined.length < 12 + 16) {
      throw new Error('Invalid ciphertext payload');
    }
    const iv = combined.slice(0, 12);
    const ciphertext = combined.slice(12);

    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      dek,
      ciphertext
    );

    const dec = new TextDecoder();
    return dec.decode(decrypted);
  }

  /**
   * Export encrypted backup with passphrase (ATVB magic envelope)
   */
  static async exportBackup(jsonData: string, passphrase: string): Promise<Uint8Array> {
    const rawSalt = crypto.getRandomValues(new Uint8Array(16));
    const backupKey = await this.deriveKek(passphrase, rawSalt);

    const enc = new TextEncoder();
    const jsonBytes = enc.encode(jsonData);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encrypted = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      backupKey,
      jsonBytes
    );

    // Envelope: [MAGIC 4B][1B salt len][salt bytes][IV 12B][encrypted data]
    const encryptedBlob = new Uint8Array(iv.length + encrypted.byteLength);
    encryptedBlob.set(iv, 0);
    encryptedBlob.set(new Uint8Array(encrypted), iv.length);

    const totalLen = BACKUP_MAGIC.length + 1 + rawSalt.length + encryptedBlob.length;
    const result = new Uint8Array(totalLen);
    let offset = 0;

    result.set(BACKUP_MAGIC, offset);
    offset += BACKUP_MAGIC.length;

    result[offset] = rawSalt.length;
    offset += 1;

    result.set(rawSalt, offset);
    offset += rawSalt.length;

    result.set(encryptedBlob, offset);
    return result;
  }

  /**
   * Import encrypted backup with passphrase
   */
  static async importBackup(backupBytes: Uint8Array, passphrase: string): Promise<string> {
    if (backupBytes.length < BACKUP_MAGIC.length + 1 + 16 + 28) {
      throw new Error('Backup file is too small or corrupted');
    }

    for (let i = 0; i < BACKUP_MAGIC.length; i++) {
      if (backupBytes[i] !== BACKUP_MAGIC[i]) {
        throw new Error('Invalid backup file format (magic mismatch)');
      }
    }

    let offset = BACKUP_MAGIC.length;
    const saltLen = backupBytes[offset];
    offset += 1;

    if (saltLen !== 16) {
      throw new Error(`Unsupported salt length: ${saltLen}`);
    }

    const salt = backupBytes.slice(offset, offset + saltLen);
    offset += saltLen;

    const encryptedBlob = backupBytes.slice(offset);
    if (encryptedBlob.length < 12 + 16) {
      throw new Error('Encrypted payload too short');
    }

    const iv = encryptedBlob.slice(0, 12);
    const ciphertext = encryptedBlob.slice(12);

    const backupKey = await this.deriveKek(passphrase, salt);
    try {
      const decrypted = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        backupKey,
        ciphertext
      );
      const dec = new TextDecoder();
      return dec.decode(decrypted);
    } catch {
      throw new Error('Incorrect backup passphrase or corrupted file');
    }
  }

  /**
   * Compute SHA-256 hex string
   */
  static async sha256(input: string): Promise<string> {
    const enc = new TextEncoder();
    const data = enc.encode(input);
    const hash = await crypto.subtle.digest('SHA-256', data);
    return Array.from(new Uint8Array(hash))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }

  /**
   * Compute HMAC-SHA-256 hex string
   */
  static async hmacSha256(keyBytes: Uint8Array, input: string): Promise<string> {
    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      keyBytes as BufferSource,
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );
    const enc = new TextEncoder();
    const sig = await crypto.subtle.sign('HMAC', cryptoKey, enc.encode(input));
    return Array.from(new Uint8Array(sig))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }

  static bytesToBase64(bytes: Uint8Array): string {
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  static base64ToBytes(base64: string): Uint8Array {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }
}
