import {
  CredentialPlain,
  CredentialPreview,
  FolderPlain,
  TagPlain,
  VaultSettingsPlain,
} from '../types';
import { VaultCrypto, VaultEnvelope } from '../crypto/vaultCrypto';

export interface DecryptedVaultData {
  folders: FolderPlain[];
  tags: TagPlain[];
  items: CredentialPlain[];
  settings: VaultSettingsPlain;
}

const STORAGE_ENVELOPE = 'atomicvault_meta_envelope';
const STORAGE_DATA = 'atomicvault_encrypted_data';
const STORAGE_BIOMETRIC_KEY = 'atomicvault_biometric_wrapped_dek';

export class VaultStorage {
  static hasVault(): boolean {
    return localStorage.getItem(STORAGE_ENVELOPE) !== null;
  }

  static getEnvelope(): VaultEnvelope | null {
    const raw = localStorage.getItem(STORAGE_ENVELOPE);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  static saveEnvelope(envelope: VaultEnvelope): void {
    localStorage.setItem(STORAGE_ENVELOPE, JSON.stringify(envelope));
  }

  static hasBiometricArmed(): boolean {
    return localStorage.getItem(STORAGE_BIOMETRIC_KEY) !== null;
  }

  static saveBiometricKey(wrappedDekBase64: string): void {
    localStorage.setItem(STORAGE_BIOMETRIC_KEY, wrappedDekBase64);
  }

  static getBiometricKey(): string | null {
    return localStorage.getItem(STORAGE_BIOMETRIC_KEY);
  }

  static clearBiometricKey(): void {
    localStorage.removeItem(STORAGE_BIOMETRIC_KEY);
  }

  static async saveVaultData(dek: CryptoKey, data: DecryptedVaultData): Promise<void> {
    const json = JSON.stringify(data);
    const ciphertext = await VaultCrypto.seal(dek, json);
    localStorage.setItem(STORAGE_DATA, ciphertext);
  }

  static async loadVaultData(dek: CryptoKey): Promise<DecryptedVaultData> {
    const raw = localStorage.getItem(STORAGE_DATA);
    if (!raw) {
      return {
        folders: [
          { id: 'f_work', name: 'Work' },
          { id: 'f_personal', name: 'Personal' },
          { id: 'f_finance', name: 'Finance' },
        ],
        tags: [
          { id: 't_fav', name: 'Favorite', color: '#10B981' },
          { id: 't_mfa', name: 'MFA Active', color: '#6366F1' },
          { id: 't_audit', name: 'Needs Review', color: '#F43F5E' },
        ],
        items: [],
        settings: {
          autoLockSeconds: 60,
          biometricEnabled: false,
          theme: 'dark',
        },
      };
    }
    const json = await VaultCrypto.open(dek, raw);
    return JSON.parse(json);
  }

  static toPreviews(
    items: CredentialPlain[],
    folderFilter?: string | null,
    query?: string,
    tagFilter?: string | null
  ): CredentialPreview[] {
    let filtered = items;

    if (folderFilter) {
      filtered = filtered.filter((i) => i.folderId === folderFilter);
    }

    if (tagFilter) {
      filtered = filtered.filter((i) => i.tags.some((t) => t.id === tagFilter));
    }

    if (query && query.trim()) {
      const q = query.toLowerCase().trim();
      filtered = filtered.filter(
        (i) =>
          i.title.toLowerCase().includes(q) ||
          i.username.toLowerCase().includes(q) ||
          (i.uriMatchPattern && i.uriMatchPattern.toLowerCase().includes(q))
      );
    }

    return filtered.map((item) => ({
      id: item.id,
      folderId: item.folderId,
      title: item.title,
      username: item.username,
      uriMatchPattern: item.uriMatchPattern,
      updatedAt: item.updatedAt,
      itemType: item.itemType,
      tags: item.tags,
    }));
  }

  static getSeedItems(): CredentialPlain[] {
    const now = Date.now();
    return [
      {
        id: 'seed_1',
        folderId: 'f_work',
        title: 'GitHub Enterprise',
        username: 'dev@atomicvault.io',
        password: 'p9$Vb#mK2!xL90sQ@87',
        notes: 'Personal access token rotated quarterly. SSH key in 1Password/Yubikey.',
        uriMatchPattern: 'github.com',
        androidPackageName: 'com.github.android',
        totpSecret: 'JBSWY3DPEHPK3PXP',
        customFields: [
          { id: 'cf1', label: 'Recovery Codes', value: 'a9b8-c7d6-e5f4', isSensitive: true },
          { id: 'cf2', label: 'Organization', value: 'Atomic-Core', isSensitive: false },
        ],
        updatedAt: now - 3600000,
        itemType: 'LOGIN',
        tags: [{ id: 't_fav', name: 'Favorite', color: '#10B981' }, { id: 't_mfa', name: 'MFA Active', color: '#6366F1' }],
      },
      {
        id: 'seed_2',
        folderId: 'f_finance',
        title: 'Mercury Business Card',
        username: '',
        password: '',
        notes: 'Virtual card linked to cloud infrastructure billing.',
        uriMatchPattern: 'mercury.com',
        androidPackageName: null,
        totpSecret: '',
        customFields: [
          { id: 'cf_card_holder', label: 'Cardholder Name', value: 'ALEX R. VAULT', isSensitive: false },
          { id: 'cf_card_num', label: 'Card Number', value: '4111 2222 3333 4444', isSensitive: true },
          { id: 'cf_card_exp', label: 'Expiration', value: '09/28', isSensitive: false },
          { id: 'cf_card_cvv', label: 'CVV / CVC', value: '882', isSensitive: true },
          { id: 'cf_card_pin', label: 'ATM PIN', value: '9420', isSensitive: true },
        ],
        updatedAt: now - 7200000,
        itemType: 'PAYMENT_CARD',
        tags: [{ id: 't_fav', name: 'Favorite', color: '#10B981' }],
      },
      {
        id: 'seed_3',
        folderId: 'f_personal',
        title: 'ProtonMail Secure',
        username: 'atomic.security@proton.me',
        password: 'Summer2024!',
        notes: 'Primary encrypted email for account recovery and pgp keys.',
        uriMatchPattern: 'proton.me',
        androidPackageName: 'ch.protonmail.android',
        totpSecret: '',
        customFields: [],
        updatedAt: now - 86400000,
        itemType: 'LOGIN',
        tags: [{ id: 't_audit', name: 'Needs Review', color: '#F43F5E' }],
      },
      {
        id: 'seed_4',
        folderId: 'f_personal',
        title: 'Emergency Contact ID',
        username: '',
        password: '',
        notes: 'Travel emergency passport copy and ICE contact phone.',
        uriMatchPattern: null,
        androidPackageName: null,
        totpSecret: '',
        customFields: [
          { id: 'cf_id_name', label: 'Full Legal Name', value: 'Alex Morgan Vance', isSensitive: false },
          { id: 'cf_id_passport', label: 'Passport No.', value: 'P129840294', isSensitive: true },
          { id: 'cf_id_phone', label: 'Emergency Phone', value: '+1 (555) 392-0941', isSensitive: false },
          { id: 'cf_id_addr', label: 'Home Address', value: '742 Evergreen Terrace, Springfield, OR', isSensitive: false },
        ],
        updatedAt: now - 172800000,
        itemType: 'IDENTITY',
        tags: [],
      },
      {
        id: 'seed_5',
        folderId: 'f_work',
        title: 'AWS Root Console',
        username: 'root@atomicvault.io',
        password: 'p9$Vb#mK2!xL90sQ@87', // Reused password to demonstrate security audit!
        notes: 'Root account credentials. Hardware FIDO2 token enforced.',
        uriMatchPattern: 'aws.amazon.com',
        androidPackageName: null,
        totpSecret: 'HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ',
        customFields: [],
        updatedAt: now - 259200000,
        itemType: 'LOGIN',
        tags: [{ id: 't_mfa', name: 'MFA Active', color: '#6366F1' }, { id: 't_audit', name: 'Needs Review', color: '#F43F5E' }],
      },
    ];
  }
}
