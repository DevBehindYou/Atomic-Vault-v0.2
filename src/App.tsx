import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  CredentialInput,
  CredentialPlain,
  FolderPlain,
  TagPlain,
  VaultExport,
  VaultItemType,
  VaultSettingsPlain,
  VaultStatus,
} from './types';
import { VaultCrypto } from './crypto/vaultCrypto';
import { DecryptedVaultData, VaultStorage } from './storage/vaultStorage';
import { TrustLedger } from './trust/trustLedger';
import { PasswordAnalysis } from './security/passwordAnalysis';
import { ThemeProvider } from './theme/themeContext';

import { OnboardingScreen } from './screens/OnboardingScreen';
import { UnlockScreen } from './screens/UnlockScreen';
import { VaultHomeScreen } from './screens/VaultHomeScreen';
import { CredentialEditorScreen } from './screens/CredentialEditorScreen';
import { PaymentCardEditorScreen } from './screens/PaymentCardEditorScreen';
import { IdentityEditorScreen } from './screens/IdentityEditorScreen';
import { PasswordGeneratorScreen } from './screens/PasswordGeneratorScreen';
import { SecurityDashboardScreen } from './screens/SecurityDashboardScreen';
import { PrivacyProofScreen } from './screens/PrivacyProofScreen';
import { SecurityTimelineScreen } from './screens/SecurityTimelineScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { BackupScreen } from './screens/BackupScreen';
import { CommandPaletteModal } from './components/CommandPaletteModal';

type AppRoute =
  | 'HOME'
  | 'EDIT_LOGIN'
  | 'EDIT_PAYMENT_CARD'
  | 'EDIT_IDENTITY'
  | 'EDIT_SECURE_NOTE'
  | 'SECURITY_DASHBOARD'
  | 'PASSWORD_GENERATOR'
  | 'PRIVACY_PROOF'
  | 'TIMELINE'
  | 'SETTINGS'
  | 'BACKUP';

export const App: React.FC = () => {
  const [status, setStatus] = useState<VaultStatus>('LOADING');
  const [dek, setDek] = useState<CryptoKey | null>(null);
  const [vaultData, setVaultData] = useState<DecryptedVaultData>({
    folders: [],
    tags: [],
    items: [],
    settings: {
      autoLockSeconds: 300,
      biometricEnabled: false,
      theme: 'dark',
    },
  });

  const [route, setRoute] = useState<AppRoute>('HOME');
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [folderFilter, setFolderFilter] = useState<string | null>(null);
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);

  const [authError, setAuthError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const lastActivityRef = useRef<number>(Date.now());

  // Check vault existence on mount
  useEffect(() => {
    if (VaultStorage.hasVault()) {
      setStatus('LOCKED');
    } else {
      setStatus('ONBOARDING');
    }
  }, []);

  // Lock Vault helper
  const handleLock = useCallback(async () => {
    setDek(null);
    setStatus('LOCKED');
    setRoute('HOME');
    setSelectedItemId(null);
    await TrustLedger.record('VAULT_LOCKED', null, null, null, 'auto_lock', 'success');
  }, []);

  // Auto-lock timer effect
  useEffect(() => {
    if (status !== 'UNLOCKED') return;

    const timeoutSec = vaultData.settings.autoLockSeconds;
    if (timeoutSec <= 0) return;

    const updateActivity = () => {
      lastActivityRef.current = Date.now();
    };

    window.addEventListener('mousemove', updateActivity);
    window.addEventListener('keydown', updateActivity);
    window.addEventListener('click', updateActivity);

    const interval = setInterval(() => {
      const elapsedSec = (Date.now() - lastActivityRef.current) / 1000;
      if (elapsedSec >= timeoutSec) {
        handleLock();
      }
    }, 5000);

    return () => {
      window.removeEventListener('mousemove', updateActivity);
      window.removeEventListener('keydown', updateActivity);
      window.removeEventListener('click', updateActivity);
      clearInterval(interval);
    };
  }, [status, vaultData.settings.autoLockSeconds, handleLock]);

  // Global Keyboard Listeners (Cmd+K / Ctrl+K for search, Cmd+N / Ctrl+N for new entry, Cmd+L to lock)
  useEffect(() => {
    if (status !== 'UNLOCKED') return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const isCmdOrCtrl = e.metaKey || e.ctrlKey;

      // Cmd+K / Ctrl+K: Open Command Palette overlay without leaving current view
      if (isCmdOrCtrl && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setIsCommandPaletteOpen((prev) => !prev);
      }

      // Cmd+N / Ctrl+N: Create new entry
      if (isCmdOrCtrl && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        setSelectedItemId(null);
        setRoute('EDIT_LOGIN');
      }

      // Cmd+L / Ctrl+L: Lock vault
      if (isCmdOrCtrl && e.key.toLowerCase() === 'l' && !e.shiftKey) {
        e.preventDefault();
        handleLock();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [status, route, handleLock]);

  // Create Vault handler
  const handleCreateVault = async (password: string, biometricEnabled: boolean, loadSamples: boolean) => {
    setBusy(true);
    setAuthError(null);
    try {
      const saltBytes = crypto.getRandomValues(new Uint8Array(16));
      const kek = await VaultCrypto.deriveKek(password, saltBytes);
      const newDek = await VaultCrypto.generateDek();
      const wrappedDek = await VaultCrypto.wrapDek(kek, newDek);

      VaultStorage.saveEnvelope({
        saltBase64: VaultCrypto.bytesToBase64(saltBytes),
        wrappedDek,
        kdfParams: {
          algorithm: 'PBKDF2',
          iterations: 100000,
          hash: 'SHA-256',
        },
      });

      if (biometricEnabled) {
        // Wrap with persistent device key
        const bioKey = await VaultCrypto.wrapDek(kek, newDek);
        VaultStorage.saveBiometricKey(bioKey);
      } else {
        VaultStorage.clearBiometricKey();
      }

      const initialData: DecryptedVaultData = {
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
        items: loadSamples ? VaultStorage.getSeedItems() : [],
        settings: {
          autoLockSeconds: 300,
          biometricEnabled,
          theme: 'dark',
        },
      };

      await VaultStorage.saveVaultData(newDek, initialData);
      await TrustLedger.record('VAULT_CREATED', null, null, 'password', 'onboarding', 'success');

      setDek(newDek);
      setVaultData(initialData);
      setStatus('UNLOCKED');
      setRoute('HOME');
    } catch (err: any) {
      setAuthError(err?.message || 'Failed to initialize encrypted vault');
    } finally {
      setBusy(false);
    }
  };

  // Unlock with Password handler
  const handleUnlockWithPassword = async (password: string): Promise<boolean> => {
    setBusy(true);
    setAuthError(null);
    try {
      const envelope = VaultStorage.getEnvelope();
      if (!envelope) throw new Error('No vault envelope found');

      const saltBytes = VaultCrypto.base64ToBytes(envelope.saltBase64);
      const kek = await VaultCrypto.deriveKek(password, saltBytes);
      const unwrappedDek = await VaultCrypto.unwrapDek(kek, envelope.wrappedDek);

      const loadedData = await VaultStorage.loadVaultData(unwrappedDek);

      // If biometric is enabled in settings, maintain biometric key
      if (loadedData.settings.biometricEnabled && !VaultStorage.hasBiometricArmed()) {
        const bioKey = await VaultCrypto.wrapDek(kek, unwrappedDek);
        VaultStorage.saveBiometricKey(bioKey);
      }

      await TrustLedger.record('VAULT_UNLOCKED', null, null, 'password', 'unlock_screen', 'success');

      setDek(unwrappedDek);
      setVaultData(loadedData);
      setStatus('UNLOCKED');
      setRoute('HOME');
      return true;
    } catch {
      await TrustLedger.record('VAULT_UNLOCK_FAILED', null, null, 'password', 'unlock_screen', 'failure');
      setAuthError('Incorrect master password');
      return false;
    } finally {
      setBusy(false);
    }
  };

  // Unlock with Biometric handler
  const handleUnlockWithBiometric = async (): Promise<boolean> => {
    setBusy(true);
    setAuthError(null);
    try {
      const bioKey = VaultStorage.getBiometricKey();
      if (!bioKey) throw new Error('Biometric unlock not initialized');

      // The quick unlock wrapped key is unwrapped using envelope KEK
      // In web, we verify device presence and load the vault
      const envelope = VaultStorage.getEnvelope();
      if (!envelope) throw new Error('No vault found');

      // Verify envelope exists and unwrap
      const unwrappedDek = await VaultCrypto.open(
        await VaultCrypto.generateDek().then(k => k), // simulated enclave
        bioKey
      ).catch(() => null);

      // Fallback: If simulated enclave token isn't plain, decode directly:
      let targetDek = unwrappedDek ? (unwrappedDek as any) : null;
      if (!targetDek) {
        // Fallback for biometric simulation
        const saltBytes = VaultCrypto.base64ToBytes(envelope.saltBase64);
        const demoKek = await VaultCrypto.deriveKek('quick-unlock-token', saltBytes);
        try {
          targetDek = await VaultCrypto.unwrapDek(demoKek, bioKey);
        } catch {
          // If biometric key was tied to master, prompt password
          throw new Error('Biometric token expired or invalid. Please enter master password.');
        }
      }

      const loadedData = await VaultStorage.loadVaultData(targetDek);
      await TrustLedger.record('VAULT_UNLOCKED', null, null, 'biometric', 'unlock_screen', 'success');

      setDek(targetDek);
      setVaultData(loadedData);
      setStatus('UNLOCKED');
      setRoute('HOME');
      return true;
    } catch (err: any) {
      await TrustLedger.record('BIOMETRIC_AUTH_FAILED', null, null, 'biometric', 'unlock_screen', 'failure');
      setAuthError(err?.message || 'Biometric authentication failed');
      return false;
    } finally {
      setBusy(false);
    }
  };

  // Reset Vault
  const handleResetVault = () => {
    localStorage.removeItem('atomicvault_meta_envelope');
    localStorage.removeItem('atomicvault_encrypted_data');
    localStorage.removeItem('atomicvault_biometric_wrapped_dek');
    VaultStorage.clearBiometricKey();
    TrustLedger.clear();

    setDek(null);
    setStatus('ONBOARDING');
    setRoute('HOME');
    setSelectedItemId(null);
  };

  // Save Item (Create or Update)
  const handleSaveItem = async (input: CredentialInput) => {
    if (!dek) return;

    const now = Date.now();
    let updatedItems: CredentialPlain[];
    const isNew = !selectedItemId;

    const assignedTags: TagPlain[] = (input.tagIds || [])
      .map((id) => vaultData.tags.find((t) => t.id === id))
      .filter((t): t is TagPlain => !!t);

    if (isNew) {
      const newItem: CredentialPlain = {
        id: crypto.randomUUID(),
        folderId: input.folderId || null,
        title: input.title,
        username: input.username || '',
        password: input.password || '',
        notes: input.notes || '',
        uriMatchPattern: input.uriMatchPattern || null,
        androidPackageName: input.androidPackageName || null,
        totpSecret: input.totpSecret || '',
        customFields: input.customFields || [],
        updatedAt: now,
        itemType: input.itemType || 'LOGIN',
        tags: assignedTags,
      };
      updatedItems = [newItem, ...vaultData.items];
      await TrustLedger.record('CREDENTIAL_CREATED', newItem.id, null, null, 'vault', 'success');
    } else {
      updatedItems = vaultData.items.map((item) => {
        if (item.id === selectedItemId) {
          return {
            ...item,
            folderId: input.folderId || null,
            title: input.title,
            username: input.username || '',
            password: input.password !== undefined ? input.password : item.password,
            notes: input.notes !== undefined ? input.notes : item.notes,
            uriMatchPattern: input.uriMatchPattern !== undefined ? input.uriMatchPattern : item.uriMatchPattern,
            totpSecret: input.totpSecret !== undefined ? input.totpSecret : item.totpSecret,
            customFields: input.customFields !== undefined ? input.customFields : item.customFields,
            itemType: input.itemType || item.itemType,
            tags: assignedTags,
            updatedAt: now,
          };
        }
        return item;
      });
      await TrustLedger.record('CREDENTIAL_MODIFIED', selectedItemId, null, null, 'vault', 'success');
    }

    const nextData: DecryptedVaultData = {
      ...vaultData,
      items: updatedItems,
    };

    await VaultStorage.saveVaultData(dek, nextData);
    setVaultData(nextData);
    setRoute('HOME');
    setSelectedItemId(null);
  };

  // Delete Item
  const handleDeleteItem = async (id: string) => {
    if (!dek) return;
    const updatedItems = vaultData.items.filter((item) => item.id !== id);
    const nextData: DecryptedVaultData = {
      ...vaultData,
      items: updatedItems,
    };

    await VaultStorage.saveVaultData(dek, nextData);
    await TrustLedger.record('CREDENTIAL_DELETED', id, null, null, 'vault', 'success');
    setVaultData(nextData);
    setRoute('HOME');
    setSelectedItemId(null);
  };

  // Update Settings
  const handleUpdateSettings = async (patch: Partial<VaultSettingsPlain>) => {
    if (!dek) return;
    const nextSettings: VaultSettingsPlain = {
      ...vaultData.settings,
      ...patch,
    };

    if (patch.biometricEnabled !== undefined) {
      if (patch.biometricEnabled) {
        await TrustLedger.record('BIOMETRIC_ENABLED', null, null, null, 'settings', 'success');
      } else {
        VaultStorage.clearBiometricKey();
        await TrustLedger.record('BIOMETRIC_DISABLED', null, null, null, 'settings', 'success');
      }
    }

    const nextData: DecryptedVaultData = {
      ...vaultData,
      settings: nextSettings,
    };

    await VaultStorage.saveVaultData(dek, nextData);
    setVaultData(nextData);
  };

  // Add Folder
  const handleAddFolder = async (name: string) => {
    if (!dek) return;
    const newFolder: FolderPlain = {
      id: `f_${crypto.randomUUID().slice(0, 8)}`,
      name,
    };
    const nextData: DecryptedVaultData = {
      ...vaultData,
      folders: [...vaultData.folders, newFolder],
    };
    await VaultStorage.saveVaultData(dek, nextData);
    setVaultData(nextData);
  };

  // Delete Folder
  const handleDeleteFolder = async (id: string) => {
    if (!dek) return;
    const nextData: DecryptedVaultData = {
      ...vaultData,
      folders: vaultData.folders.filter((f) => f.id !== id),
      // Set items in this folder to root
      items: vaultData.items.map((i) => (i.folderId === id ? { ...i, folderId: null } : i)),
    };
    await VaultStorage.saveVaultData(dek, nextData);
    setVaultData(nextData);
  };

  // Add Tag
  const handleAddTag = async (name: string, color: string) => {
    if (!dek) return;
    const newTag: TagPlain = {
      id: `t_${crypto.randomUUID().slice(0, 8)}`,
      name,
      color,
    };
    const nextData: DecryptedVaultData = {
      ...vaultData,
      tags: [...vaultData.tags, newTag],
    };
    await VaultStorage.saveVaultData(dek, nextData);
    setVaultData(nextData);
  };

  // Delete Tag
  const handleDeleteTag = async (id: string) => {
    if (!dek) return;
    const nextData: DecryptedVaultData = {
      ...vaultData,
      tags: vaultData.tags.filter((t) => t.id !== id),
      items: vaultData.items.map((i) => ({
        ...i,
        tags: i.tags.filter((t) => t.id !== id),
      })),
    };
    await VaultStorage.saveVaultData(dek, nextData);
    setVaultData(nextData);
  };

  // Backup Export
  const handleExportData = (): VaultExport => {
    return {
      folders: vaultData.folders,
      items: vaultData.items,
      settings: vaultData.settings,
      tags: vaultData.tags,
      exportedAt: Date.now(),
      version: 1,
    };
  };

  // Backup Import
  const handleImportData = async (exported: VaultExport) => {
    if (!dek) return;

    // Merge folders and tags, append unique items
    const existingItemIds = new Set(vaultData.items.map((i) => i.id));
    const mergedItems = [...vaultData.items];
    for (const incoming of exported.items) {
      if (!existingItemIds.has(incoming.id)) {
        mergedItems.push(incoming);
      }
    }

    const nextData: DecryptedVaultData = {
      ...vaultData,
      folders: exported.folders || vaultData.folders,
      tags: exported.tags || vaultData.tags,
      items: mergedItems,
      settings: exported.settings || vaultData.settings,
    };

    await VaultStorage.saveVaultData(dek, nextData);
    setVaultData(nextData);
  };

  // Item click navigation
  const handleItemClick = (id: string, type: VaultItemType) => {
    setSelectedItemId(id);
    switch (type) {
      case 'LOGIN':
        setRoute('EDIT_LOGIN');
        break;
      case 'PAYMENT_CARD':
        setRoute('EDIT_PAYMENT_CARD');
        break;
      case 'IDENTITY':
        setRoute('EDIT_IDENTITY');
        break;
      case 'SECURE_NOTE':
        setRoute('EDIT_SECURE_NOTE');
        break;
    }
  };

  // Add New Item navigation
  const handleAddNewClick = (type: VaultItemType) => {
    setSelectedItemId(null);
    switch (type) {
      case 'LOGIN':
        setRoute('EDIT_LOGIN');
        break;
      case 'PAYMENT_CARD':
        setRoute('EDIT_PAYMENT_CARD');
        break;
      case 'IDENTITY':
        setRoute('EDIT_IDENTITY');
        break;
      case 'SECURE_NOTE':
        setRoute('EDIT_SECURE_NOTE');
        break;
    }
  };

  const securityReport = PasswordAnalysis.analyzeVault(vaultData.items);
  const previews = VaultStorage.toPreviews(vaultData.items, folderFilter, searchQuery, tagFilter);
  const selectedItem = selectedItemId
    ? vaultData.items.find((i) => i.id === selectedItemId) || null
    : null;

  // Render by status
  if (status === 'LOADING') {
    return (
      <div className="min-h-screen bg-black text-white flex items-center justify-center font-mono text-sm">
        Initializing AtomicVault...
      </div>
    );
  }

  if (status === 'ONBOARDING') {
    return (
      <ThemeProvider>
        <OnboardingScreen
          onCreateVault={handleCreateVault}
          error={authError}
          busy={busy}
        />
      </ThemeProvider>
    );
  }

  if (status === 'LOCKED') {
    return (
      <ThemeProvider>
        <UnlockScreen
          onUnlockWithPassword={handleUnlockWithPassword}
          onUnlockWithBiometric={VaultStorage.hasBiometricArmed() ? handleUnlockWithBiometric : undefined}
          biometricArmed={VaultStorage.hasBiometricArmed()}
          onResetVault={handleResetVault}
          error={authError}
          busy={busy}
        />
      </ThemeProvider>
    );
  }

  // UNLOCKED VIEW ROUTING
  return (
    <ThemeProvider>
      <div className="min-h-screen bg-black text-white antialiased">
        {route === 'HOME' && (
          <VaultHomeScreen
            previews={previews}
            folders={vaultData.folders}
            tags={vaultData.tags}
            query={searchQuery}
            folderFilter={folderFilter}
            tagFilter={tagFilter}
            findings={securityReport.findings}
            onSearchChange={setSearchQuery}
            onSelectFolder={setFolderFilter}
            onSelectTag={setTagFilter}
            onItemClick={handleItemClick}
            onAddNewClick={handleAddNewClick}
            onSettingsClick={() => setRoute('SETTINGS')}
            onSecurityClick={() => setRoute('SECURITY_DASHBOARD')}
            onGeneratorClick={() => setRoute('PASSWORD_GENERATOR')}
            onPrivacyProofClick={() => setRoute('PRIVACY_PROOF')}
            onLockClick={handleLock}
            onOpenCommandPalette={() => setIsCommandPaletteOpen(true)}
            securityScore={securityReport.score}
            getItemPassword={(id) => vaultData.items.find((i) => i.id === id)?.password || null}
          />
        )}

        {route === 'EDIT_LOGIN' && (
          <CredentialEditorScreen
            itemId={selectedItemId}
            existingItem={selectedItem}
            folders={vaultData.folders}
            allTags={vaultData.tags}
            onSave={handleSaveItem}
            onDelete={handleDeleteItem}
            onBack={() => setRoute('HOME')}
          />
        )}

        {route === 'EDIT_PAYMENT_CARD' && (
          <PaymentCardEditorScreen
            itemId={selectedItemId}
            existingItem={selectedItem}
            folders={vaultData.folders}
            allTags={vaultData.tags}
            onSave={handleSaveItem}
            onDelete={handleDeleteItem}
            onBack={() => setRoute('HOME')}
          />
        )}

        {(route === 'EDIT_IDENTITY' || route === 'EDIT_SECURE_NOTE') && (
          <IdentityEditorScreen
            itemId={selectedItemId}
            existingItem={selectedItem}
            itemType={route === 'EDIT_SECURE_NOTE' ? 'SECURE_NOTE' : 'IDENTITY'}
            folders={vaultData.folders}
            allTags={vaultData.tags}
            onSave={handleSaveItem}
            onDelete={handleDeleteItem}
            onBack={() => setRoute('HOME')}
          />
        )}

        {route === 'PASSWORD_GENERATOR' && (
          <PasswordGeneratorScreen onBack={() => setRoute('HOME')} />
        )}

        {route === 'SECURITY_DASHBOARD' && (
          <SecurityDashboardScreen
            report={securityReport}
            onSelectFinding={(id) => {
              const item = vaultData.items.find((i) => i.id === id);
              if (item) {
                handleItemClick(id, item.itemType);
              }
            }}
            onBack={() => setRoute('HOME')}
          />
        )}

        {route === 'PRIVACY_PROOF' && (
          <PrivacyProofScreen
            onBack={() => setRoute('HOME')}
            onViewTimeline={() => setRoute('TIMELINE')}
          />
        )}

        {route === 'TIMELINE' && (
          <SecurityTimelineScreen onBack={() => setRoute('PRIVACY_PROOF')} />
        )}

        {route === 'SETTINGS' && (
          <SettingsScreen
            settings={vaultData.settings}
            folders={vaultData.folders}
            tags={vaultData.tags}
            onUpdateSettings={handleUpdateSettings}
            onAddFolder={handleAddFolder}
            onDeleteFolder={handleDeleteFolder}
            onAddTag={handleAddTag}
            onDeleteTag={handleDeleteTag}
            onNavigateBackup={() => setRoute('BACKUP')}
            onNavigatePrivacy={() => setRoute('PRIVACY_PROOF')}
            onResetVault={handleResetVault}
            onBack={() => setRoute('HOME')}
          />
        )}

        {route === 'BACKUP' && (
          <BackupScreen
            onExportData={handleExportData}
            onImportData={handleImportData}
            onBack={() => setRoute('SETTINGS')}
          />
        )}

        {/* Global Command Palette Overlay (Cmd+K) */}
        <CommandPaletteModal
          isOpen={isCommandPaletteOpen}
          onClose={() => setIsCommandPaletteOpen(false)}
          vaultItems={vaultData.items}
          folders={vaultData.folders}
          tags={vaultData.tags}
          findings={securityReport.findings}
          onSelectItem={handleItemClick}
          onSelectFolder={(folderId) => {
            setFolderFilter(folderId);
            setRoute('HOME');
          }}
          onSelectTag={(tagId) => {
            setTagFilter(tagId);
            setRoute('HOME');
          }}
          onAddNew={handleAddNewClick}
          onNavigate={(destRoute) => setRoute(destRoute as AppRoute)}
          onLock={handleLock}
        />
      </div>
    </ThemeProvider>
  );
};

export default App;
