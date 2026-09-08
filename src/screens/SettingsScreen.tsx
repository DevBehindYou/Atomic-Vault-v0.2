import React, { useState } from 'react';
import {
  ArrowLeft,
  Fingerprint,
  Moon,
  Sun,
  Database,
  ShieldCheck,
  Trash2,
  Plus,
  AlertCircle,
} from 'lucide-react';
import { FolderPlain, TagPlain, VaultSettingsPlain } from '../types';
import { LiquidGlassCard } from '../components/LiquidGlassCard';
import { useTheme } from '../theme/themeContext';

interface SettingsScreenProps {
  settings: VaultSettingsPlain;
  folders: FolderPlain[];
  tags: TagPlain[];
  onUpdateSettings: (patch: Partial<VaultSettingsPlain>) => void;
  onAddFolder: (name: string) => void;
  onDeleteFolder: (id: string) => void;
  onAddTag: (name: string, color: string) => void;
  onDeleteTag: (id: string) => void;
  onNavigateBackup: () => void;
  onNavigatePrivacy: () => void;
  onResetVault: () => void;
  onBack: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  settings,
  folders,
  tags,
  onUpdateSettings,
  onAddFolder,
  onDeleteFolder,
  onAddTag,
  onDeleteTag,
  onNavigateBackup,
  onNavigatePrivacy,
  onResetVault,
  onBack,
}) => {
  const { theme, toggleTheme } = useTheme();
  const [newFolderName, setNewFolderName] = useState('');
  const [newTagName, setNewTagName] = useState('');
  const [newTagColor, setNewTagColor] = useState('#10B981');
  const [showResetConfirm, setShowResetConfirm] = useState(false);

  const handleCreateFolder = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;
    onAddFolder(newFolderName.trim());
    setNewFolderName('');
  };

  const handleCreateTag = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTagName.trim()) return;
    onAddTag(newTagName.trim(), newTagColor);
    setNewTagName('');
  };

  return (
    <div className="min-h-screen bg-black text-white pb-20">
      <header className="sticky top-0 z-20 bg-black/80 backdrop-blur-md border-b border-white/10 px-4 sm:px-8 py-3.5 flex items-center gap-3">
        <button
          type="button"
          onClick={onBack}
          className="p-1.5 -ml-1 rounded-xl text-neutral-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h2 className="font-bold text-base sm:text-lg text-white">Settings & Vault Config</h2>
      </header>

      <main className="max-w-2xl mx-auto px-4 sm:px-6 pt-6 space-y-6">
        {/* Security Preferences */}
        <div className="space-y-2">
          <h3 className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">
            Security & Locking
          </h3>
          <LiquidGlassCard className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <div className="font-medium text-sm text-white">Auto-Lock Timer</div>
                <div className="text-xs text-neutral-400">Lock vault after period of inactivity</div>
              </div>
              <select
                value={settings.autoLockSeconds}
                onChange={(e) =>
                  onUpdateSettings({ autoLockSeconds: parseInt(e.target.value, 10) })
                }
                className="bg-white/10 border border-white/15 rounded-xl px-3 py-1.5 text-xs text-white outline-none cursor-pointer"
              >
                <option value={60} className="bg-neutral-900">1 minute</option>
                <option value={300} className="bg-neutral-900">5 minutes</option>
                <option value={900} className="bg-neutral-900">15 minutes</option>
                <option value={1800} className="bg-neutral-900">30 minutes</option>
                <option value={0} className="bg-neutral-900">Never (manual only)</option>
              </select>
            </div>

            <div className="h-px bg-white/10" />

            <label className="flex items-center justify-between cursor-pointer">
              <div className="space-y-0.5">
                <div className="font-medium text-sm text-white flex items-center gap-2">
                  <Fingerprint className="w-4 h-4 text-emerald-400" />
                  <span>Quick Biometric Unlock</span>
                </div>
                <div className="text-xs text-neutral-400">
                  Allow one-click unlock using client-stored cryptographic token
                </div>
              </div>
              <input
                type="checkbox"
                checked={settings.biometricEnabled}
                onChange={(e) => onUpdateSettings({ biometricEnabled: e.target.checked })}
                className="accent-white w-4 h-4 rounded cursor-pointer"
              />
            </label>

            <div className="h-px bg-white/10" />

            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <div className="font-medium text-sm text-white flex items-center gap-2">
                  {theme === 'dark' ? <Moon className="w-4 h-4 text-indigo-400" /> : <Sun className="w-4 h-4 text-amber-400" />}
                  <span>Appearance Theme</span>
                </div>
                <div className="text-xs text-neutral-400">
                  Current theme is {theme === 'dark' ? 'Dark Obsidian' : 'Light Crystal'}
                </div>
              </div>
              <button
                type="button"
                onClick={toggleTheme}
                className="px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/15 text-xs text-white cursor-pointer font-medium"
              >
                Switch to {theme === 'dark' ? 'Light' : 'Dark'}
              </button>
            </div>
          </LiquidGlassCard>
        </div>

        {/* Vault Data & Backups */}
        <div className="space-y-2">
          <h3 className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">
            Data, Sync & Proofs
          </h3>
          <LiquidGlassCard className="space-y-2 p-2">
            <button
              type="button"
              onClick={onNavigateBackup}
              className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors text-left cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <Database className="w-4 h-4 text-white" />
                <div>
                  <div className="font-medium text-sm text-white">Encrypted Backup & Restore</div>
                  <div className="text-xs text-neutral-400">Export or import password-protected .atvb backup files</div>
                </div>
              </div>
            </button>

            <button
              type="button"
              onClick={onNavigatePrivacy}
              className="w-full flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors text-left cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <div>
                  <div className="font-medium text-sm text-white">Trust & Privacy Proof</div>
                  <div className="text-xs text-neutral-400">Verify offline guarantees & audit ledger integrity</div>
                </div>
              </div>
            </button>
          </LiquidGlassCard>
        </div>

        {/* Manage Folders */}
        <div className="space-y-2">
          <h3 className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">
            Folders
          </h3>
          <LiquidGlassCard className="space-y-3">
            <form onSubmit={handleCreateFolder} className="flex gap-2">
              <input
                type="text"
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                placeholder="New folder name..."
                className="flex-1 bg-white/5 border border-white/15 rounded-xl px-3 py-1.5 text-xs text-white outline-none"
              />
              <button
                type="submit"
                className="px-3 py-1.5 bg-white text-black font-semibold text-xs rounded-xl hover:bg-neutral-200 cursor-pointer flex items-center gap-1"
              >
                <Plus className="w-3.5 h-3.5" />
                Add
              </button>
            </form>

            <div className="space-y-1.5">
              {folders.map((f) => (
                <div key={f.id} className="flex items-center justify-between p-2 rounded-xl bg-white/5 text-xs">
                  <span className="text-white font-medium">{f.name}</span>
                  <button
                    type="button"
                    onClick={() => onDeleteFolder(f.id)}
                    className="p-1 text-neutral-400 hover:text-rose-400 cursor-pointer"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          </LiquidGlassCard>
        </div>

        {/* Manage Tags */}
        <div className="space-y-2">
          <h3 className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">
            Tags
          </h3>
          <LiquidGlassCard className="space-y-3">
            <form onSubmit={handleCreateTag} className="flex gap-2 items-center">
              <input
                type="text"
                value={newTagName}
                onChange={(e) => setNewTagName(e.target.value)}
                placeholder="New tag label..."
                className="flex-1 bg-white/5 border border-white/15 rounded-xl px-3 py-1.5 text-xs text-white outline-none"
              />
              <input
                type="color"
                value={newTagColor}
                onChange={(e) => setNewTagColor(e.target.value)}
                className="w-8 h-8 rounded-lg border border-white/15 bg-transparent cursor-pointer"
              />
              <button
                type="submit"
                className="px-3 py-1.5 bg-white text-black font-semibold text-xs rounded-xl hover:bg-neutral-200 cursor-pointer flex items-center gap-1"
              >
                <Plus className="w-3.5 h-3.5" />
                Add
              </button>
            </form>

            <div className="flex flex-wrap gap-2">
              {tags.map((t) => (
                <div
                  key={t.id}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/5 border border-white/15 text-xs text-white"
                >
                  <span
                    className="w-2 h-2 rounded-full inline-block"
                    style={{ backgroundColor: t.color || '#fff' }}
                  />
                  <span>{t.name}</span>
                  <button
                    type="button"
                    onClick={() => onDeleteTag(t.id)}
                    className="text-neutral-400 hover:text-rose-400 ml-1 cursor-pointer"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              ))}
            </div>
          </LiquidGlassCard>
        </div>

        {/* Danger Zone */}
        <div className="space-y-2 pt-2">
          <h3 className="text-xs font-semibold text-rose-400 uppercase tracking-wider">
            Danger Zone
          </h3>
          <LiquidGlassCard className="border-rose-500/30 space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-sm font-semibold text-white">Reset Encrypted Vault</div>
                <div className="text-xs text-neutral-400">Permanently erase all keys and stored credentials</div>
              </div>
              <button
                type="button"
                onClick={() => setShowResetConfirm(true)}
                className="px-3 py-1.5 bg-rose-500 hover:bg-rose-600 text-white font-semibold text-xs rounded-xl transition-colors cursor-pointer"
              >
                Reset Vault
              </button>
            </div>
          </LiquidGlassCard>
        </div>

        {showResetConfirm && (
          <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <LiquidGlassCard className="max-w-sm w-full space-y-4 border-rose-500/40">
              <div className="flex items-center gap-2 text-rose-400">
                <AlertCircle className="w-5 h-5" />
                <h3 className="font-bold text-base text-white">Are you completely sure?</h3>
              </div>
              <p className="text-xs text-neutral-300 leading-relaxed">
                This will delete your master envelope, local data encryption key, and all stored
                passwords and records. This action cannot be undone.
              </p>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowResetConfirm(false)}
                  className="px-3 py-1.5 text-xs rounded-xl bg-white/10 hover:bg-white/15 text-neutral-300 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowResetConfirm(false);
                    onResetVault();
                  }}
                  className="px-3 py-1.5 text-xs rounded-xl bg-rose-500 hover:bg-rose-600 text-white font-semibold cursor-pointer"
                >
                  Erase Everything
                </button>
              </div>
            </LiquidGlassCard>
          </div>
        )}
      </main>
    </div>
  );
};
