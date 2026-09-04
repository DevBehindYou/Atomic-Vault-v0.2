import { TrustEventType, TrustLedgerEntry } from '../types';
import { VaultCrypto } from '../crypto/vaultCrypto';

const GENESIS_HASH = 'GENESIS';
const LEDGER_STORAGE_KEY = 'atomicvault_trust_ledger_v1';
const LEDGER_HMAC_KEY_STORAGE = 'atomicvault_trust_ledger_hmackey';

export class TrustLedger {
  private static hmacKeyBytes: Uint8Array | null = null;

  private static async getOrCreateHmacKey(): Promise<Uint8Array> {
    if (this.hmacKeyBytes) return this.hmacKeyBytes;
    const stored = localStorage.getItem(LEDGER_HMAC_KEY_STORAGE);
    if (stored) {
      this.hmacKeyBytes = VaultCrypto.base64ToBytes(stored);
      return this.hmacKeyBytes;
    }
    const fresh = crypto.getRandomValues(new Uint8Array(32));
    localStorage.setItem(LEDGER_HMAC_KEY_STORAGE, VaultCrypto.bytesToBase64(fresh));
    this.hmacKeyBytes = fresh;
    return fresh;
  }

  static async record(
    eventType: TrustEventType,
    subjectReference?: string | null,
    targetPackage?: string | null,
    authenticationType?: string | null,
    source = 'app',
    result = 'success'
  ): Promise<void> {
    try {
      const entries = this.listEntries(1000);
      const previousHash = entries.length > 0 ? entries[0].eventHash : GENESIS_HASH;
      const timestamp = Date.now();
      const subjectHash = subjectReference ? await VaultCrypto.sha256(subjectReference) : null;
      const packageHash = targetPackage ? await VaultCrypto.sha256(targetPackage) : null;

      const payload = `${previousHash}|${timestamp}|${eventType}|${subjectHash || ''}|${packageHash || ''}|${authenticationType || ''}|${source}|${result}`;
      const hmacKey = await this.getOrCreateHmacKey();
      const eventHash = await VaultCrypto.hmacSha256(hmacKey, payload);

      const entry: TrustLedgerEntry = {
        id: crypto.randomUUID(),
        timestamp,
        eventType,
        subjectReferenceHash: subjectHash,
        targetPackageHash: packageHash,
        authenticationType: authenticationType || null,
        source,
        result,
        previousHash,
        eventHash,
      };

      // Store at beginning of array for fast newest-first queries
      entries.unshift(entry);
      // Keep up to 500 entries
      const pruned = entries.slice(0, 500);
      localStorage.setItem(LEDGER_STORAGE_KEY, JSON.stringify(pruned));
    } catch (err) {
      console.error('Failed to record trust ledger event', err);
    }
  }

  static listEntries(limit = 200): TrustLedgerEntry[] {
    try {
      const raw = localStorage.getItem(LEDGER_STORAGE_KEY);
      if (!raw) return [];
      const parsed: TrustLedgerEntry[] = JSON.parse(raw);
      return parsed.slice(0, limit);
    } catch {
      return [];
    }
  }

  /**
   * Walks the whole chain in chronological order, re-deriving every HMAC
   * and confirming both the hash itself and the previous-hash link match.
   * Returns null if valid, or the id of the broken entry.
   */
  static async verifyChainIntegrity(): Promise<string | null> {
    try {
      const raw = localStorage.getItem(LEDGER_STORAGE_KEY);
      if (!raw) return null;
      const all: TrustLedgerEntry[] = JSON.parse(raw);
      if (all.length === 0) return null;

      // Reverse so we examine from oldest (GENESIS) to newest
      const ascending = [...all].sort((a, b) => a.timestamp - b.timestamp);
      const hmacKey = await this.getOrCreateHmacKey();
      let expectedPrevious = GENESIS_HASH;

      for (const entry of ascending) {
        if (entry.previousHash !== expectedPrevious) {
          return entry.id;
        }

        const payload = `${entry.previousHash}|${entry.timestamp}|${entry.eventType}|${entry.subjectReferenceHash || ''}|${entry.targetPackageHash || ''}|${entry.authenticationType || ''}|${entry.source}|${entry.result}`;
        const recomputed = await VaultCrypto.hmacSha256(hmacKey, payload);

        if (recomputed !== entry.eventHash) {
          return entry.id;
        }

        expectedPrevious = entry.eventHash;
      }
      return null;
    } catch (err) {
      console.error('Chain verification error:', err);
      return 'VERIFICATION_ERROR';
    }
  }

  static clear(): void {
    localStorage.removeItem(LEDGER_STORAGE_KEY);
  }
}
