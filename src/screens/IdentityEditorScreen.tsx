import React, { useState, useEffect } from 'react';
import { ArrowLeft, Trash2, Eye, EyeOff, Folder as FolderIcon } from 'lucide-react';
import { CredentialInput, CredentialPlain, FolderPlain, TagPlain, CustomFieldPlain, VaultItemType } from '../types';
import { LiquidGlassCard } from '../components/LiquidGlassCard';

interface IdentityEditorScreenProps {
  itemId?: string | null;
  existingItem?: CredentialPlain | null;
  itemType?: VaultItemType;
  folders: FolderPlain[];
  allTags: TagPlain[];
  onSave: (input: CredentialInput) => Promise<void>;
  onDelete?: (id: string) => Promise<void>;
  onBack: () => void;
}

export const IdentityEditorScreen: React.FC<IdentityEditorScreenProps> = ({
  itemId,
  existingItem,
  itemType = 'IDENTITY',
  folders,
  allTags: _allTags,
  onSave,
  onDelete,
  onBack,
}) => {
  const isSecureNote = itemType === 'SECURE_NOTE';

  const [title, setTitle] = useState(existingItem?.title || '');
  const [fullName, setFullName] = useState('');
  const [docNumber, setDocNumber] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState(existingItem?.notes || '');
  const [folderId, setFolderId] = useState<string | null>(existingItem?.folderId || null);
  const [showDocNumber, setShowDocNumber] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (existingItem) {
      setTitle(existingItem.title);
      setNotes(existingItem.notes);
      setFolderId(existingItem.folderId || null);

      const fields = existingItem.customFields || [];
      const getVal = (lbl: string) => fields.find((f) => f.label.toLowerCase() === lbl.toLowerCase())?.value || '';
      setFullName(getVal('Full Legal Name') || existingItem.username);
      setDocNumber(getVal('Passport / ID Number') || getVal('Passport No.'));
      setPhone(getVal('Phone Number') || getVal('Emergency Phone'));
      setAddress(getVal('Home Address'));
    }
  }, [existingItem]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setError('Title is required');
      return;
    }

    setSaving(true);
    setError(null);

    const customFields: CustomFieldPlain[] = isSecureNote
      ? []
      : [
          { id: 'cf_name', label: 'Full Legal Name', value: fullName.trim(), isSensitive: false },
          { id: 'cf_doc', label: 'Passport / ID Number', value: docNumber.trim(), isSensitive: true },
          { id: 'cf_phone', label: 'Phone Number', value: phone.trim(), isSensitive: false },
          { id: 'cf_addr', label: 'Home Address', value: address.trim(), isSensitive: false },
        ];

    try {
      await onSave({
        title: title.trim(),
        username: isSecureNote ? '' : fullName.trim(),
        password: '',
        notes: notes.trim(),
        folderId,
        customFields,
        itemType: isSecureNote ? 'SECURE_NOTE' : 'IDENTITY',
      });
    } catch (err: any) {
      setError(err?.message || 'Failed to save item');
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-black text-white pb-20">
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
            {itemId
              ? isSecureNote ? 'Edit Secure Note' : 'Edit Identity'
              : isSecureNote ? 'New Secure Note' : 'New Identity Record'}
          </h2>
        </div>

        <div className="flex items-center gap-2">
          {itemId && onDelete && (
            <button
              type="button"
              onClick={() => {
                if (confirm('Delete this item?')) {
                  onDelete(itemId);
                }
              }}
              className="p-2 rounded-xl text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
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

      <main className="max-w-xl mx-auto px-4 sm:px-6 pt-6 space-y-6">
        {error && (
          <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl">
            {error}
          </div>
        )}

        <LiquidGlassCard className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-neutral-300">Title / Label</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={isSecureNote ? 'e.g. WiFi Passwords, Server Config' : 'e.g. Passport, Driver License, SSN'}
              required
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none"
            />
          </div>

          {!isSecureNote && (
            <>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-neutral-300">Full Legal Name</label>
                <input
                  type="text"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="First Middle Last"
                  className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none"
                />
              </div>

              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-medium text-neutral-300">Passport / License / Tax ID Number</label>
                  <button
                    type="button"
                    onClick={() => setShowDocNumber(!showDocNumber)}
                    className="text-neutral-400 hover:text-white"
                  >
                    {showDocNumber ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  </button>
                </div>
                <input
                  type={showDocNumber ? 'text' : 'password'}
                  value={docNumber}
                  onChange={(e) => setDocNumber(e.target.value)}
                  placeholder="Identification number"
                  className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white font-mono placeholder-neutral-500 outline-none"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-neutral-300">Phone Number</label>
                  <input
                    type="text"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="+1 (555) 000-0000"
                    className="w-full bg-white/5 border border-white/15 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-neutral-300">Address</label>
                  <input
                    type="text"
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                    placeholder="City, State, Zip"
                    className="w-full bg-white/5 border border-white/15 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none"
                  />
                </div>
              </div>
            </>
          )}

          <div className="space-y-1.5 pt-1">
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
                No Folder
              </option>
              {folders.map((f) => (
                <option key={f.id} value={f.id} className="bg-neutral-900 text-white">
                  {f.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-neutral-300">
              {isSecureNote ? 'Note Content (Encrypted)' : 'Additional Notes / Instructions'}
            </label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={isSecureNote ? 10 : 4}
              placeholder={isSecureNote ? 'Write your secret note here...' : 'Emergency contact details, expiration dates...'}
              className="w-full bg-white/5 border border-white/15 rounded-xl p-3 text-xs text-white placeholder-neutral-500 outline-none resize-y"
            />
          </div>
        </LiquidGlassCard>
      </main>
    </div>
  );
};
