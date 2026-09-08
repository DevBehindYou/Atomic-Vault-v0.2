import React, { useState, useEffect } from 'react';
import { ArrowLeft, Copy, Check, Trash2, Eye, EyeOff, Folder as FolderIcon } from 'lucide-react';
import { CredentialInput, CredentialPlain, FolderPlain, TagPlain, CustomFieldPlain } from '../types';
import { LiquidGlassCard } from '../components/LiquidGlassCard';

interface PaymentCardEditorScreenProps {
  itemId?: string | null;
  existingItem?: CredentialPlain | null;
  folders: FolderPlain[];
  allTags: TagPlain[];
  onSave: (input: CredentialInput) => Promise<void>;
  onDelete?: (id: string) => Promise<void>;
  onBack: () => void;
}

export const PaymentCardEditorScreen: React.FC<PaymentCardEditorScreenProps> = ({
  itemId,
  existingItem,
  folders,
  allTags: _allTags,
  onSave,
  onDelete,
  onBack,
}) => {
  const [title, setTitle] = useState(existingItem?.title || '');
  const [cardholder, setCardholder] = useState('');
  const [cardNumber, setCardNumber] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvv, setCvv] = useState('');
  const [pin, setPin] = useState('');
  const [notes, setNotes] = useState(existingItem?.notes || '');
  const [folderId, setFolderId] = useState<string | null>(existingItem?.folderId || null);
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>(
    existingItem?.tags.map((t) => t.id) || []
  );
  const [showCvv, setShowCvv] = useState(false);
  const [showPin, setShowPin] = useState(false);
  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (existingItem) {
      setTitle(existingItem.title);
      setNotes(existingItem.notes);
      setFolderId(existingItem.folderId || null);
      setSelectedTagIds(existingItem.tags.map((t) => t.id));

      const fields = existingItem.customFields || [];
      const getVal = (lbl: string) => fields.find((f) => f.label.toLowerCase() === lbl.toLowerCase())?.value || '';
      setCardholder(getVal('Cardholder Name'));
      setCardNumber(getVal('Card Number'));
      setExpiry(getVal('Expiration'));
      setCvv(getVal('CVV / CVC'));
      setPin(getVal('ATM PIN'));
    }
  }, [existingItem]);

  // Card brand detection
  const getBrand = () => {
    const clean = cardNumber.replace(/\s+/g, '');
    if (clean.startsWith('4')) return 'VISA';
    if (/^5[1-5]/.test(clean) || /^2[2-7]/.test(clean)) return 'MASTERCARD';
    if (/^3[47]/.test(clean)) return 'AMEX';
    if (/^6(?:011|5)/.test(clean)) return 'DISCOVER';
    return 'CARD';
  };

  const formatCardNumber = (val: string) => {
    const digits = val.replace(/\D/g, '').slice(0, 16);
    return digits.replace(/(\d{4})(?=\d)/g, '$1 ');
  };

  const formatExpiry = (val: string) => {
    const digits = val.replace(/\D/g, '').slice(0, 4);
    if (digits.length >= 2) {
      return `${digits.slice(0, 2)}/${digits.slice(2)}`;
    }
    return digits;
  };

  const copyText = async (text: string, fieldName: string) => {
    if (!text) return;
    await navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(null), 1500);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setError('Card label/title is required');
      return;
    }

    setSaving(true);
    setError(null);

    const customFields: CustomFieldPlain[] = [
      { id: 'cf_ch', label: 'Cardholder Name', value: cardholder.trim(), isSensitive: false },
      { id: 'cf_cn', label: 'Card Number', value: cardNumber.trim(), isSensitive: true },
      { id: 'cf_exp', label: 'Expiration', value: expiry.trim(), isSensitive: false },
      { id: 'cf_cvv', label: 'CVV / CVC', value: cvv.trim(), isSensitive: true },
      { id: 'cf_pin', label: 'ATM PIN', value: pin.trim(), isSensitive: true },
    ];

    try {
      await onSave({
        title: title.trim(),
        username: cardholder.trim(),
        password: '',
        notes: notes.trim(),
        folderId,
        tagIds: selectedTagIds,
        customFields,
        itemType: 'PAYMENT_CARD',
      });
    } catch (err: any) {
      setError(err?.message || 'Failed to save payment card');
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-black text-white pb-20">
      {/* Header */}
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
            {itemId ? 'Edit Payment Card' : 'New Payment Card'}
          </h2>
        </div>

        <div className="flex items-center gap-2">
          {itemId && onDelete && (
            <button
              type="button"
              onClick={() => {
                if (confirm('Delete this payment card?')) {
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

        {/* Visual Card Mockup */}
        <div className="relative w-full aspect-[1.586] rounded-2xl p-6 liquid-glass border border-white/30 shadow-2xl flex flex-col justify-between overflow-hidden">
          <div className="flex justify-between items-start">
            <div className="space-y-1">
              <span className="text-[11px] uppercase tracking-widest text-neutral-400 font-mono">
                Encrypted Card
              </span>
              <div className="text-lg font-bold text-white tracking-wide">
                {title || 'Card Title'}
              </div>
            </div>
            <span className="font-mono text-xs font-bold px-2 py-1 bg-white/10 rounded-md border border-white/20">
              {getBrand()}
            </span>
          </div>

          <div className="font-mono text-lg sm:text-xl tracking-widest text-white/90 font-medium">
            {cardNumber || '•••• •••• •••• ••••'}
          </div>

          <div className="flex justify-between items-end text-xs">
            <div>
              <div className="text-[9px] uppercase text-neutral-400">Cardholder</div>
              <div className="font-mono uppercase font-semibold text-white">
                {cardholder || 'YOUR NAME'}
              </div>
            </div>
            <div>
              <div className="text-[9px] uppercase text-neutral-400 text-right">Expires</div>
              <div className="font-mono font-semibold text-white">{expiry || 'MM/YY'}</div>
            </div>
          </div>
        </div>

        {/* Card Form */}
        <LiquidGlassCard className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-neutral-300">Card Label / Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Chase Sapphire, Mercury Debit"
              required
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-neutral-300">Cardholder Name</label>
            <input
              type="text"
              value={cardholder}
              onChange={(e) => setCardholder(e.target.value)}
              placeholder="Name as printed on card"
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none uppercase"
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-neutral-300">Card Number</label>
              {cardNumber && (
                <button
                  type="button"
                  onClick={() => copyText(cardNumber.replace(/\s+/g, ''), 'cardNum')}
                  className="text-xs text-neutral-400 hover:text-white flex items-center gap-1 cursor-pointer"
                >
                  {copiedField === 'cardNum' ? (
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                  <span>{copiedField === 'cardNum' ? 'Copied' : 'Copy'}</span>
                </button>
              )}
            </div>
            <input
              type="text"
              value={cardNumber}
              onChange={(e) => setCardNumber(formatCardNumber(e.target.value))}
              placeholder="4111 2222 3333 4444"
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white font-mono placeholder-neutral-500 outline-none"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-neutral-300">Expiration (MM/YY)</label>
              <input
                type="text"
                value={expiry}
                onChange={(e) => setExpiry(formatExpiry(e.target.value))}
                placeholder="MM/YY"
                className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white font-mono placeholder-neutral-500 outline-none"
              />
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-neutral-300">Security Code (CVV)</label>
                <button
                  type="button"
                  onClick={() => setShowCvv(!showCvv)}
                  className="text-neutral-400 hover:text-white"
                >
                  {showCvv ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                </button>
              </div>
              <input
                type={showCvv ? 'text' : 'password'}
                value={cvv}
                onChange={(e) => setCvv(e.target.value.slice(0, 4))}
                placeholder="123"
                className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white font-mono placeholder-neutral-500 outline-none"
              />
            </div>
          </div>

          <div className="space-y-1.5 pt-1">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-neutral-300">ATM PIN (Optional)</label>
              <button
                type="button"
                onClick={() => setShowPin(!showPin)}
                className="text-neutral-400 hover:text-white"
              >
                {showPin ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
              </button>
            </div>
            <input
              type={showPin ? 'text' : 'password'}
              value={pin}
              onChange={(e) => setPin(e.target.value.slice(0, 6))}
              placeholder="4-6 digit PIN"
              className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white font-mono placeholder-neutral-500 outline-none"
            />
          </div>
        </LiquidGlassCard>

        {/* Organization */}
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
            <label className="text-xs font-medium text-neutral-300">Card Notes</label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
              placeholder="Billing address, bank phone, credit limits..."
              className="w-full bg-white/5 border border-white/15 rounded-xl p-3 text-xs text-white placeholder-neutral-500 outline-none resize-y"
            />
          </div>
        </LiquidGlassCard>
      </main>
    </div>
  );
};
