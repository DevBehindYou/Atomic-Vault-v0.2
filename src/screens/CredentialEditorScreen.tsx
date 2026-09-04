import React, { useState, useEffect } from 'react';
import {
  ArrowLeft,
  Eye,
  EyeOff,
  Copy,
  Check,
  Sparkles,
  Plus,
  Trash2,
  Globe,
  Tag as TagIcon,
  Folder as FolderIcon,
  Shield,
  FileText,
  QrCode,
  Smartphone,
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { CredentialInput, CredentialPlain, CustomFieldPlain, FolderPlain, TagPlain } from '../types';
import { LiquidGlassCard } from '../components/LiquidGlassCard';
import { EntropyMeter } from '../components/EntropyMeter';
import { PasswordAnalysis } from '../security/passwordAnalysis';
import { PasswordGeneratorPanel } from '../components/PasswordGeneratorPanel';
import { TotpDisplay } from '../components/TotpDisplay';

interface CredentialEditorScreenProps {
  itemId?: string | null;
  existingItem?: CredentialPlain | null;
  folders: FolderPlain[];
  allTags: TagPlain[];
  onSave: (input: CredentialInput) => Promise<void>;
  onDelete?: (id: string) => Promise<void>;
  onBack: () => void;
}

export const CredentialEditorScreen: React.FC<CredentialEditorScreenProps> = ({
  itemId,
  existingItem,
  folders,
  allTags,
  onSave,
  onDelete,
  onBack,
}) => {
  const [title, setTitle] = useState(existingItem?.title || '');
  const [username, setUsername] = useState(existingItem?.username || '');
  const [password, setPassword] = useState(existingItem?.password || '');
  const [notes, setNotes] = useState(existingItem?.notes || '');
  const [uriMatchPattern, setUriMatchPattern] = useState(existingItem?.uriMatchPattern || '');
  const [totpSecret, setTotpSecret] = useState(existingItem?.totpSecret || '');
  const [folderId, setFolderId] = useState<string | null>(existingItem?.folderId || null);
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>(
    existingItem?.tags.map((t) => t.id) || []
  );
  const [customFields, setCustomFields] = useState<CustomFieldPlain[]>(
    existingItem?.customFields || []
  );

  const [showPassword, setShowPassword] = useState(false);
  const [showGenerator, setShowGenerator] = useState(false);
  const [showQrCode, setShowQrCode] = useState(false);
  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (existingItem) {
      setTitle(existingItem.title);
      setUsername(existingItem.username);
      setPassword(existingItem.password);
      setNotes(existingItem.notes);
      setUriMatchPattern(existingItem.uriMatchPattern || '');
      setTotpSecret(existingItem.totpSecret || '');
      setFolderId(existingItem.folderId || null);
      setSelectedTagIds(existingItem.tags.map((t) => t.id));
      setCustomFields(existingItem.customFields || []);
    }
  }, [existingItem]);

  const copyText = async (text: string, fieldName: string) => {
    if (!text) return;
    await navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(null), 1500);
  };

  const handleToggleTag = (tagId: string) => {
    if (selectedTagIds.includes(tagId)) {
      setSelectedTagIds(selectedTagIds.filter((id) => id !== tagId));
    } else {
      setSelectedTagIds([...selectedTagIds, tagId]);
    }
  };

  const addCustomField = () => {
    setCustomFields([
      ...customFields,
      {
        id: crypto.randomUUID(),
        label: 'Field ' + (customFields.length + 1),
        value: '',
        isSensitive: false,
      },
    ]);
  };

  const updateCustomField = (id: string, updates: Partial<CustomFieldPlain>) => {
    setCustomFields(
      customFields.map((cf) => (cf.id === id ? { ...cf, ...updates } : cf))
    );
  };

  const removeCustomField = (id: string) => {
    setCustomFields(customFields.filter((cf) => cf.id !== id));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setError('Title is required');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSave({
        title: title.trim(),
        username: username.trim(),
        password,
        notes: notes.trim(),
        uriMatchPattern: uriMatchPattern.trim() || null,
        totpSecret: totpSecret.trim(),
        folderId,
        tagIds: selectedTagIds,
        customFields,
        itemType: 'LOGIN',
      });
    } catch (err: any) {
      setError(err?.message || 'Failed to save item');
      setSaving(false);
    }
  };

  const entropy = PasswordAnalysis.estimateEntropyBits(password);

  return (
    <div className="min-h-screen bg-black text-white pb-20">
      {/* Top Header */}
      <header className="sticky top-0 z-20 bg-black/80 backdrop-blur-md border-b border-white/10 px-4 sm:px-8 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onBack}
            className="p-1.5 -ml-1 rounded-xl text-neutral-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <h2 className="font-bold text-base sm:text-lg text-white">
            {itemId ? 'Edit Credential' : 'New Login Credential'}
          </h2>
        </div>

        <div className="flex items-center gap-2">
          {itemId && onDelete && (
            <button
              type="button"
              onClick={() => {
                if (confirm('Delete this credential permanently?')) {
                  onDelete(itemId);
                }
              }}
              className="p-2 rounded-xl text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
              title="Delete credential"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
          <button
            type="button"
            onClick={handleSubmit}
            disabled={saving}
            className="px-4 py-1.5 bg-white text-black font-semibold text-xs rounded-xl hover:bg-neutral-200 transition-colors cursor-pointer disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </header>

      {/* Main Form */}
      <main className="max-w-2xl mx-auto px-4 sm:px-6 pt-6 space-y-6">
        {error && (
          <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl">
            {error}
          </div>
        )}

        {/* Primary Details Card */}
        <LiquidGlassCard className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-neutral-300">Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. GitHub, Google, Slack"
              required
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none transition-all"
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-neutral-300">Username / Email</label>
              {username && (
                <button
                  type="button"
                  onClick={() => copyText(username, 'username')}
                  className="text-xs text-neutral-400 hover:text-white flex items-center gap-1 cursor-pointer"
                >
                  {copiedField === 'username' ? (
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                  <span>{copiedField === 'username' ? 'Copied' : 'Copy'}</span>
                </button>
              )}
            </div>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="username or email"
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none transition-all"
            />
          </div>

          {/* Password Section */}
          <div className="space-y-1.5 pt-1">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-neutral-300">Password</label>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => setShowGenerator(!showGenerator)}
                  className="text-xs text-neutral-300 hover:text-white flex items-center gap-1 cursor-pointer"
                >
                  <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                  <span>{showGenerator ? 'Close Generator' : 'Generate'}</span>
                </button>
                {password && (
                  <button
                    type="button"
                    onClick={() => copyText(password, 'password')}
                    className="text-xs text-neutral-400 hover:text-white flex items-center gap-1 cursor-pointer"
                  >
                    {copiedField === 'password' ? (
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                    ) : (
                      <Copy className="w-3.5 h-3.5" />
                    )}
                    <span>{copiedField === 'password' ? 'Copied' : 'Copy'}</span>
                  </button>
                )}
              </div>
            </div>

            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Password"
                className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 pr-10 outline-none transition-all font-mono"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-white cursor-pointer"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>

            {password && <EntropyMeter entropy={entropy} className="pt-1" />}
          </div>

          {showGenerator && (
            <PasswordGeneratorPanel
              onSelectPassword={(pw) => {
                setPassword(pw);
                setShowGenerator(false);
              }}
              className="mt-3 border-amber-500/20"
            />
          )}

          {/* Website / URI Match */}
          <div className="space-y-1.5 pt-1">
            <label className="text-xs font-medium text-neutral-300 flex items-center gap-1.5">
              <Globe className="w-3.5 h-3.5 text-neutral-400" />
              <span>Website / Domain Match</span>
            </label>
            <input
              type="text"
              value={uriMatchPattern}
              onChange={(e) => setUriMatchPattern(e.target.value)}
              placeholder="e.g. github.com"
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none transition-all"
            />
          </div>
        </LiquidGlassCard>

        {/* 2FA / TOTP Section */}
        <LiquidGlassCard className="space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Shield className="w-4 h-4 text-emerald-400" />
              <h3 className="font-semibold text-sm text-white">Two-Factor Authentication (TOTP)</h3>
            </div>
            {totpSecret.trim() && (
              <button
                type="button"
                onClick={() => setShowQrCode(!showQrCode)}
                className={`text-xs px-2.5 py-1 rounded-lg border transition-all flex items-center gap-1.5 cursor-pointer ${
                  showQrCode
                    ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                    : 'bg-white/5 border-white/10 hover:bg-white/10 text-neutral-300'
                }`}
              >
                <QrCode className="w-3.5 h-3.5" />
                <span>{showQrCode ? 'Hide QR Code' : 'Show Mobile QR'}</span>
              </button>
            )}
          </div>
          <div className="space-y-1.5">
            <label className="text-xs text-neutral-400">TOTP Secret Key (Base32)</label>
            <input
              type="text"
              value={totpSecret}
              onChange={(e) => setTotpSecret(e.target.value)}
              placeholder="e.g. JBSWY3DPEHPK3PXP"
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none transition-all font-mono uppercase"
            />
          </div>

          {totpSecret.trim() && (
            <>
              <TotpDisplay secret={totpSecret.trim()} />

              {showQrCode && (
                <div className="p-4 rounded-xl bg-black/40 border border-white/15 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Smartphone className="w-4 h-4 text-emerald-400" />
                      <span className="text-xs font-semibold text-white">Scan with Authenticator App</span>
                    </div>
                    <span className="text-[10px] text-neutral-400 font-mono">RFC 6238 Standard</span>
                  </div>

                  <div className="flex flex-col sm:flex-row items-center gap-4 pt-1">
                    <div className="p-3 bg-white rounded-xl shadow-lg shrink-0 flex items-center justify-center">
                      <QRCodeSVG
                        value={`otpauth://totp/${encodeURIComponent(title.trim() || 'AtomicVault')}:${encodeURIComponent(
                          username.trim() || 'user'
                        )}?secret=${totpSecret.replace(/[\s-]/g, '').toUpperCase()}&issuer=${encodeURIComponent(
                          title.trim() || 'AtomicVault'
                        )}&algorithm=SHA1&digits=6&period=30`}
                        size={160}
                        level="M"
                        includeMargin={false}
                      />
                    </div>

                    <div className="space-y-2.5 text-xs text-neutral-300">
                      <p className="leading-relaxed">
                        Scan this QR code with <strong className="text-white">Google Authenticator</strong>, <strong className="text-white">Aegis</strong>, <strong className="text-white">1Password</strong>, or <strong className="text-white">Apple Passwords</strong> on your mobile phone to sync two-factor codes offline.
                      </p>
                      <div className="p-2.5 rounded-lg bg-white/5 border border-white/10 space-y-1">
                        <div className="flex items-center justify-between text-[11px] text-neutral-400 font-mono">
                          <span>Account: <span className="text-white">{username.trim() || 'user'}</span></span>
                          <span>Issuer: <span className="text-white">{title.trim() || 'AtomicVault'}</span></span>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          const uri = `otpauth://totp/${encodeURIComponent(title.trim() || 'AtomicVault')}:${encodeURIComponent(
                            username.trim() || 'user'
                          )}?secret=${totpSecret.replace(/[\s-]/g, '').toUpperCase()}&issuer=${encodeURIComponent(
                            title.trim() || 'AtomicVault'
                          )}&algorithm=SHA1&digits=6&period=30`;
                          copyText(uri, 'totp_uri');
                        }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs font-medium cursor-pointer transition-colors"
                      >
                        {copiedField === 'totp_uri' ? (
                          <Check className="w-3.5 h-3.5 text-emerald-400" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                        <span>{copiedField === 'totp_uri' ? 'URI Copied' : 'Copy otpauth:// URI'}</span>
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </LiquidGlassCard>

        {/* Folder & Tags Organization */}
        <LiquidGlassCard className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-neutral-300 flex items-center gap-1.5">
              <FolderIcon className="w-3.5 h-3.5 text-neutral-400" />
              <span>Folder</span>
            </label>
            <select
              value={folderId || ''}
              onChange={(e) => setFolderId(e.target.value || null)}
              className="w-full bg-white/5 border border-white/15 rounded-xl px-3.5 py-2.5 text-sm text-white outline-none cursor-pointer"
            >
              <option value="" className="bg-neutral-900 text-white">
                No Folder (Root)
              </option>
              {folders.map((f) => (
                <option key={f.id} value={f.id} className="bg-neutral-900 text-white">
                  {f.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <label className="text-xs font-medium text-neutral-300 flex items-center gap-1.5">
              <TagIcon className="w-3.5 h-3.5 text-neutral-400" />
              <span>Tags</span>
            </label>
            <div className="flex flex-wrap gap-2">
              {allTags.map((tag) => {
                const isSelected = selectedTagIds.includes(tag.id);
                return (
                  <button
                    key={tag.id}
                    type="button"
                    onClick={() => handleToggleTag(tag.id)}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium border cursor-pointer transition-all flex items-center gap-1.5 ${
                      isSelected
                        ? 'bg-white text-black border-white'
                        : 'bg-white/5 text-neutral-300 border-white/15 hover:bg-white/10'
                    }`}
                  >
                    {tag.color && (
                      <span
                        className="w-2 h-2 rounded-full inline-block"
                        style={{ backgroundColor: tag.color }}
                      />
                    )}
                    <span>{tag.name}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </LiquidGlassCard>

        {/* Custom Fields Section */}
        <LiquidGlassCard className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-sm text-white">Custom Fields</h3>
            <button
              type="button"
              onClick={addCustomField}
              className="inline-flex items-center gap-1 text-xs text-neutral-300 hover:text-white px-2.5 py-1 rounded-lg bg-white/5 hover:bg-white/10 cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Field</span>
            </button>
          </div>

          {customFields.length === 0 ? (
            <p className="text-xs text-neutral-500 italic">No custom fields added yet.</p>
          ) : (
            <div className="space-y-3">
              {customFields.map((cf) => (
                <div key={cf.id} className="p-3 rounded-xl bg-white/5 border border-white/10 space-y-2">
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={cf.label}
                      onChange={(e) => updateCustomField(cf.id, { label: e.target.value })}
                      placeholder="Field Label (e.g. Recovery Key)"
                      className="flex-1 bg-transparent border-b border-white/15 px-1 py-1 text-xs text-white font-medium outline-none"
                    />
                    <label className="flex items-center gap-1 text-[11px] text-neutral-400 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={cf.isSensitive}
                        onChange={(e) => updateCustomField(cf.id, { isSensitive: e.target.checked })}
                        className="accent-white rounded w-3.5 h-3.5"
                      />
                      <span>Sensitive</span>
                    </label>
                    <button
                      type="button"
                      onClick={() => removeCustomField(cf.id)}
                      className="text-neutral-500 hover:text-rose-400 p-1"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <input
                    type={cf.isSensitive ? 'password' : 'text'}
                    value={cf.value}
                    onChange={(e) => updateCustomField(cf.id, { value: e.target.value })}
                    placeholder="Field Value"
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-2.5 py-1.5 text-xs text-white outline-none"
                  />
                </div>
              ))}
            </div>
          )}
        </LiquidGlassCard>

        {/* Notes Textarea */}
        <LiquidGlassCard className="space-y-2">
          <label className="text-xs font-medium text-neutral-300 flex items-center gap-1.5">
            <FileText className="w-3.5 h-3.5 text-neutral-400" />
            <span>Secure Notes</span>
          </label>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={4}
            placeholder="Encrypted notes, PINs, recovery instructions..."
            className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl p-3 text-xs text-white placeholder-neutral-500 outline-none resize-y"
          />
        </LiquidGlassCard>
      </main>
    </div>
  );
};
