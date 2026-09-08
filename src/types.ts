export type VaultItemType = 'LOGIN' | 'PAYMENT_CARD' | 'IDENTITY' | 'SECURE_NOTE';

export interface CustomFieldPlain {
  id: string;
  label: string;
  value: string;
  isSensitive: boolean;
}

export interface FolderPlain {
  id: string;
  name: string;
  parentId?: string | null;
}

export interface TagPlain {
  id: string;
  name: string;
  color?: string | null;
}

export interface CredentialPlain {
  id: string;
  folderId: string | null;
  title: string;
  username: string;
  password: string;
  notes: string;
  uriMatchPattern: string | null;
  androidPackageName: string | null;
  totpSecret: string;
  customFields: CustomFieldPlain[];
  updatedAt: number;
  itemType: VaultItemType;
  tags: TagPlain[];
}

export interface CredentialInput {
  folderId?: string | null;
  title: string;
  username?: string;
  password?: string;
  notes?: string;
  uriMatchPattern?: string | null;
  androidPackageName?: string | null;
  totpSecret?: string;
  customFields?: CustomFieldPlain[];
  itemType?: VaultItemType;
  tagIds?: string[];
}

export interface CredentialPreview {
  id: string;
  folderId: string | null;
  title: string;
  username: string;
  uriMatchPattern: string | null;
  updatedAt: number;
  itemType: VaultItemType;
  tags: TagPlain[];
}

export interface VaultSettingsPlain {
  autoLockSeconds: number;
  biometricEnabled: boolean;
  theme?: 'dark' | 'light';
}

export interface VaultExport {
  folders: FolderPlain[];
  items: CredentialPlain[];
  settings: VaultSettingsPlain;
  tags: TagPlain[];
  exportedAt: number;
  version: number;
}

export type VaultStatus = 'LOADING' | 'ONBOARDING' | 'LOCKED' | 'UNLOCKED';

export type TrustEventType =
  | 'VAULT_CREATED'
  | 'VAULT_UNLOCKED'
  | 'VAULT_UNLOCK_FAILED'
  | 'VAULT_LOCKED'
  | 'CREDENTIAL_FILLED'
  | 'CREDENTIAL_CREATED'
  | 'CREDENTIAL_MODIFIED'
  | 'CREDENTIAL_DELETED'
  | 'PASSWORD_GENERATED'
  | 'BIOMETRIC_ENABLED'
  | 'BIOMETRIC_DISABLED'
  | 'BIOMETRIC_AUTH_FAILED'
  | 'BACKUP_EXPORTED'
  | 'BACKUP_IMPORTED'
  | 'SECURITY_SETTING_CHANGED'
  | 'INTEGRITY_CHECK_COMPLETED';

export interface TrustLedgerEntry {
  id: string;
  timestamp: number;
  eventType: TrustEventType;
  subjectReferenceHash: string | null;
  targetPackageHash: string | null;
  authenticationType: string | null;
  source: string;
  result: string;
  previousHash: string;
  eventHash: string;
}

export type PasswordIssue = 'EMPTY' | 'REUSED' | 'WEAK';

export interface CredentialFinding {
  credential: CredentialPlain;
  issues: PasswordIssue[];
  entropy: number;
}

export interface VaultSecurityReport {
  score: number;
  reusedCount: number;
  weakCount: number;
  emptyCount: number;
  totalCount: number;
  findings: CredentialFinding[];
}

export interface PrivacyCheck {
  category: string;
  label: string;
  passed: boolean;
  detail: string;
  isLiveCheck: boolean;
}

export interface GeneratorOptions {
  length: number;
  lower: boolean;
  upper: boolean;
  digits: boolean;
  symbols: boolean;
  avoidAmbiguous: boolean;
}

export type Strength = 'WEAK' | 'FAIR' | 'STRONG' | 'EXCELLENT';
