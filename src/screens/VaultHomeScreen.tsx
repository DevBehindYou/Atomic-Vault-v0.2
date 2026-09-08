import React, { useState, useRef, useEffect, useMemo } from 'react';
import {
  Search,
  Lock,
  Settings,
  Plus,
  Key,
  CreditCard,
  UserCheck,
  FileText,
  Copy,
  Check,
  ShieldCheck,
  Sparkles,
  ChevronRight,
  ShieldAlert,
  ArrowUpDown,
  ChevronDown,
  AlertTriangle,
  AlertCircle,
} from 'lucide-react';
import { CredentialPreview, FolderPlain, TagPlain, VaultItemType, CredentialFinding, PasswordIssue } from '../types';
import { LiquidGlassCard } from '../components/LiquidGlassCard';
import { FilterChipPill } from '../components/FilterChipPill';

export type SortOption = 'MODIFIED' | 'ALPHABETICAL' | 'TYPE';

/**
 * Highlights matching text segments when search query is typed.
 */
const HighlightMatch: React.FC<{
  text: string | null | undefined;
  query: string;
  className?: string;
}> = ({ text, query, className }) => {
  if (!text) return null;
  if (!query || !query.trim()) {
    return <span className={className}>{text}</span>;
  }
  const cleanQuery = query.trim();
  const escaped = cleanQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(`(${escaped})`, 'gi');
  const parts = text.split(regex);

  return (
    <span className={className}>
      {parts.map((part, i) =>
        part.toLowerCase() === cleanQuery.toLowerCase() ? (
          <mark
            key={i}
            className="bg-amber-400/35 text-amber-200 rounded-xs px-0.5 font-semibold not-italic"
          >
            {part}
          </mark>
        ) : (
          part
        )
      )}
    </span>
  );
};

interface VaultHomeScreenProps {
  previews: CredentialPreview[];
  folders: FolderPlain[];
  tags: TagPlain[];
  query: string;
  folderFilter: string | null;
  tagFilter: string | null;
  findings?: CredentialFinding[];
  onSearchChange: (q: string) => void;
  onSelectFolder: (id: string | null) => void;
  onSelectTag: (id: string | null) => void;
  onItemClick: (id: string, type: VaultItemType) => void;
  onAddNewClick: (type: VaultItemType) => void;
  onSettingsClick: () => void;
  onSecurityClick: () => void;
  onGeneratorClick: () => void;
  onPrivacyProofClick: () => void;
  onLockClick: () => void;
  onOpenCommandPalette?: () => void;
  securityScore?: number;
  getItemPassword?: (id: string) => string | null;
}

export const VaultHomeScreen: React.FC<VaultHomeScreenProps> = ({
  previews,
  folders,
  tags,
  query,
  folderFilter,
  tagFilter,
  findings = [],
  onSearchChange,
  onSelectFolder,
  onSelectTag,
  onItemClick,
  onAddNewClick,
  onSettingsClick,
  onSecurityClick,
  onGeneratorClick,
  onPrivacyProofClick,
  onLockClick,
  onOpenCommandPalette,
  securityScore = 100,
  getItemPassword,
}) => {
  const [showAddMenu, setShowAddMenu] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [sortBy, setSortBy] = useState<SortOption>('MODIFIED');
  const [showSortMenu, setShowSortMenu] = useState(false);

  const searchInputRef = useRef<HTMLInputElement>(null);
  const sortMenuRef = useRef<HTMLDivElement>(null);

  // Close sort menu on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (sortMenuRef.current && !sortMenuRef.current.contains(e.target as Node)) {
        setShowSortMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Map weak / reused / empty findings by credential ID
  const issueMap = useMemo(() => {
    const map = new Map<string, PasswordIssue[]>();
    for (const f of findings) {
      map.set(f.credential.id, f.issues);
    }
    return map;
  }, [findings]);

  // Sort previews according to user's selected Sort Option
  const sortedPreviews = useMemo(() => {
    const items = [...previews];
    if (sortBy === 'MODIFIED') {
      return items.sort((a, b) => b.updatedAt - a.updatedAt);
    }
    if (sortBy === 'ALPHABETICAL') {
      return items.sort((a, b) => a.title.localeCompare(b.title, undefined, { sensitivity: 'base' }));
    }
    if (sortBy === 'TYPE') {
      const TYPE_ORDER: Record<VaultItemType, number> = {
        LOGIN: 1,
        PAYMENT_CARD: 2,
        IDENTITY: 3,
        SECURE_NOTE: 4,
      };
      return items.sort((a, b) => {
        const orderDiff = (TYPE_ORDER[a.itemType] || 99) - (TYPE_ORDER[b.itemType] || 99);
        if (orderDiff !== 0) return orderDiff;
        return a.title.localeCompare(b.title, undefined, { sensitivity: 'base' });
      });
    }
    return items;
  }, [previews, sortBy]);

  useEffect(() => {
    const handleFocusSearch = () => {
      searchInputRef.current?.focus();
      searchInputRef.current?.select();
    };
    window.addEventListener('vault:focus-search', handleFocusSearch);
    return () => window.removeEventListener('vault:focus-search', handleFocusSearch);
  }, []);

  const getItemIcon = (type: VaultItemType) => {
    switch (type) {
      case 'LOGIN':
        return <Key className="w-4 h-4 text-emerald-400" />;
      case 'PAYMENT_CARD':
        return <CreditCard className="w-4 h-4 text-cyan-400" />;
      case 'IDENTITY':
        return <UserCheck className="w-4 h-4 text-purple-400" />;
      case 'SECURE_NOTE':
        return <FileText className="w-4 h-4 text-amber-400" />;
    }
  };

  const handleCopyUsername = async (e: React.MouseEvent, text: string, id: string) => {
    e.stopPropagation();
    if (!text) return;
    await navigator.clipboard.writeText(text);
    setCopiedId(`user_${id}`);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const handleCopyPassword = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    const pw = getItemPassword ? getItemPassword(id) : null;
    if (pw) {
      await navigator.clipboard.writeText(pw);
      setCopiedId(`pw_${id}`);
      setTimeout(() => setCopiedId(null), 1500);
    }
  };

  return (
    <div className="min-h-screen bg-black text-white pb-24">
      {/* Top Navbar */}
      <header className="sticky top-0 z-30 bg-black/80 backdrop-blur-md border-b border-white/10 px-4 sm:px-8 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-white/10 border border-white/20 flex items-center justify-center">
            <Lock className="w-4 h-4 text-white" />
          </div>
          <div>
            <h1 className="font-bold text-base sm:text-lg tracking-tight text-white leading-none">AtomicVault</h1>
            <span className="text-[10px] text-emerald-400 font-mono tracking-wider">OFFLINE ENCRYPTED</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onPrivacyProofClick}
            title="Privacy Proof & Verification"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/20 text-xs font-medium cursor-pointer transition-colors"
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Trust Proof</span>
          </button>

          <button
            type="button"
            onClick={onSecurityClick}
            title="Security Audit & Password Health"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/5 border border-white/15 text-neutral-200 hover:bg-white/10 text-xs font-medium cursor-pointer transition-colors"
          >
            <ShieldAlert className="w-3.5 h-3.5 text-amber-400" />
            <span className="font-mono">{securityScore}%</span>
          </button>

          <button
            type="button"
            onClick={onSettingsClick}
            title="Settings"
            className="p-2 rounded-xl text-neutral-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
          >
            <Settings className="w-4 h-4" />
          </button>

          <button
            type="button"
            onClick={onLockClick}
            title="Lock Vault"
            className="px-3 py-1.5 rounded-xl bg-white text-black font-semibold text-xs hover:bg-neutral-200 transition-colors cursor-pointer"
          >
            Lock
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 pt-5 space-y-4">
        {/* Search Bar */}
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
          <input
            ref={searchInputRef}
            type="text"
            value={query}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search vault items by title, username, or URL..."
            className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-2xl pl-10 pr-14 py-2.5 text-sm text-white placeholder-neutral-500 outline-none transition-all"
          />
          {query ? (
            <button
              type="button"
              onClick={() => onSearchChange('')}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-xs text-neutral-400 hover:text-white cursor-pointer"
            >
              Clear
            </button>
          ) : (
            <button
              type="button"
              onClick={onOpenCommandPalette}
              title="Open Command Palette (Cmd+K)"
              className="absolute right-3 top-1/2 -translate-y-1/2 hidden sm:flex items-center gap-1 text-[10px] font-mono text-neutral-400 hover:text-white bg-white/10 hover:bg-white/15 px-2 py-0.5 rounded-lg border border-white/10 cursor-pointer transition-colors"
            >
              <span>⌘K</span>
            </button>
          )}
        </div>

        {/* Quick Utilities Row */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar text-xs">
          <button
            type="button"
            onClick={onGeneratorClick}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/5 border border-white/10 hover:bg-white/10 text-neutral-300 transition-colors shrink-0 cursor-pointer"
          >
            <Sparkles className="w-3.5 h-3.5 text-white" />
            <span>Password Generator</span>
          </button>
          <div className="h-4 w-px bg-white/10 shrink-0 mx-1" />

          {/* Folder Pills */}
          <FilterChipPill
            label="All Items"
            selected={folderFilter === null}
            onClick={() => onSelectFolder(null)}
            count={previews.length}
          />
          {folders.map((f) => (
            <FilterChipPill
              key={f.id}
              label={f.name}
              selected={folderFilter === f.id}
              onClick={() => onSelectFolder(folderFilter === f.id ? null : f.id)}
            />
          ))}
        </div>

        {/* Tag Pills */}
        {tags.length > 0 && (
          <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar text-xs">
            <span className="text-[11px] text-neutral-500 uppercase tracking-wider shrink-0 mr-1">Tags:</span>
            {tags.map((t) => (
              <FilterChipPill
                key={t.id}
                label={t.name}
                colorDot={t.color}
                selected={tagFilter === t.id}
                onClick={() => onSelectTag(tagFilter === t.id ? null : t.id)}
              />
            ))}
          </div>
        )}

        {/* List Toolbar (Counts & Sort By Dropdown) */}
        <div className="flex items-center justify-between pt-1 text-xs text-neutral-400">
          <div className="font-mono text-[11px] text-neutral-500">
            Showing {sortedPreviews.length} {sortedPreviews.length === 1 ? 'item' : 'items'}
            {folderFilter && ' in selected folder'}
            {tagFilter && ' with selected tag'}
          </div>

          {/* Sort By Dropdown Menu */}
          <div className="relative" ref={sortMenuRef}>
            <button
              type="button"
              onClick={() => setShowSortMenu(!showSortMenu)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-neutral-300 hover:text-white transition-colors cursor-pointer text-xs"
              title="Toggle Sorting Order"
            >
              <ArrowUpDown className="w-3.5 h-3.5 text-neutral-400" />
              <span>
                Sort:{' '}
                <span className="font-medium text-white">
                  {sortBy === 'MODIFIED'
                    ? 'Last Modified'
                    : sortBy === 'ALPHABETICAL'
                    ? 'Alphabetical'
                    : 'Item Type'}
                </span>
              </span>
              <ChevronDown className="w-3.5 h-3.5 text-neutral-400 ml-0.5" />
            </button>

            {showSortMenu && (
              <div className="absolute right-0 mt-1.5 w-44 rounded-xl bg-neutral-900 border border-white/15 shadow-2xl py-1 z-30 space-y-0.5">
                <button
                  type="button"
                  onClick={() => {
                    setSortBy('MODIFIED');
                    setShowSortMenu(false);
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2 text-xs text-left cursor-pointer transition-colors ${
                    sortBy === 'MODIFIED'
                      ? 'bg-white/15 text-white font-semibold'
                      : 'text-neutral-300 hover:bg-white/5'
                  }`}
                >
                  <span>Last Modified</span>
                  {sortBy === 'MODIFIED' && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSortBy('ALPHABETICAL');
                    setShowSortMenu(false);
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2 text-xs text-left cursor-pointer transition-colors ${
                    sortBy === 'ALPHABETICAL'
                      ? 'bg-white/15 text-white font-semibold'
                      : 'text-neutral-300 hover:bg-white/5'
                  }`}
                >
                  <span>Alphabetical (A-Z)</span>
                  {sortBy === 'ALPHABETICAL' && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSortBy('TYPE');
                    setShowSortMenu(false);
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2 text-xs text-left cursor-pointer transition-colors ${
                    sortBy === 'TYPE'
                      ? 'bg-white/15 text-white font-semibold'
                      : 'text-neutral-300 hover:bg-white/5'
                  }`}
                >
                  <span>Item Type</span>
                  {sortBy === 'TYPE' && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Items List */}
        <div className="space-y-2 pt-1">
          {sortedPreviews.length === 0 ? (
            <LiquidGlassCard className="text-center py-12 space-y-3">
              <div className="w-12 h-12 rounded-2xl bg-white/5 border border-white/10 mx-auto flex items-center justify-center text-neutral-400">
                <Search className="w-6 h-6" />
              </div>
              <h3 className="font-semibold text-sm text-neutral-200">No vault items found</h3>
              <p className="text-xs text-neutral-400 max-w-xs mx-auto">
                {query || folderFilter || tagFilter
                  ? 'Try clearing search filters or add a new credential'
                  : 'Your vault is ready. Add your first encrypted item below.'}
              </p>
              <button
                type="button"
                onClick={() => onAddNewClick('LOGIN')}
                className="mt-2 inline-flex items-center gap-1.5 px-4 py-2 bg-white text-black text-xs font-semibold rounded-xl hover:bg-neutral-200 transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                Add Login
              </button>
            </LiquidGlassCard>
          ) : (
            sortedPreviews.map((item) => {
              const itemIssues = issueMap.get(item.id);
              const isReused = itemIssues?.includes('REUSED');
              const isWeak = itemIssues?.includes('WEAK');
              const isEmpty = itemIssues?.includes('EMPTY');

              return (
                <LiquidGlassCard
                  key={item.id}
                  onClick={() => onItemClick(item.id, item.itemType)}
                  className={`group flex items-center justify-between gap-4 p-3.5 sm:p-4 transition-all cursor-pointer ${
                    isReused
                      ? 'border-rose-500/40 hover:border-rose-500/60 bg-rose-500/[0.03]'
                      : isWeak
                      ? 'border-amber-500/40 hover:border-amber-500/60 bg-amber-500/[0.03]'
                      : 'hover:border-white/30'
                  }`}
                >
                  <div className="flex items-center gap-3.5 min-w-0">
                    <div
                      className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border ${
                        isReused
                          ? 'bg-rose-500/10 border-rose-500/30'
                          : isWeak
                          ? 'bg-amber-500/10 border-amber-500/30'
                          : 'bg-white/5 border border-white/10'
                      }`}
                    >
                      {getItemIcon(item.itemType)}
                    </div>
                    <div className="min-w-0 space-y-0.5">
                      <div className="flex items-center gap-2 flex-wrap">
                        <HighlightMatch
                          text={item.title}
                          query={query}
                          className="font-semibold text-sm text-white truncate"
                        />
                        {item.uriMatchPattern && (
                          <HighlightMatch
                            text={item.uriMatchPattern}
                            query={query}
                            className="hidden sm:inline-block text-[11px] text-neutral-500 font-mono truncate max-w-[150px]"
                          />
                        )}

                        {/* Weak / Reused Password Visual Indicators */}
                        {isReused && (
                          <span
                            className="inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/40 shrink-0"
                            title="Reused password detected by PasswordAnalysis"
                          >
                            <AlertTriangle className="w-3 h-3 text-rose-400" />
                            <span>Reused Password</span>
                          </span>
                        )}
                        {!isReused && isWeak && (
                          <span
                            className="inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/40 shrink-0"
                            title="Weak password (<50 bits entropy) detected by PasswordAnalysis"
                          >
                            <AlertCircle className="w-3 h-3 text-amber-400" />
                            <span>Weak Password</span>
                          </span>
                        )}
                        {!isReused && !isWeak && isEmpty && item.itemType === 'LOGIN' && (
                          <span
                            className="inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full bg-neutral-500/20 text-neutral-300 border border-neutral-500/30 shrink-0"
                            title="Password field is empty"
                          >
                            <AlertCircle className="w-3 h-3 text-neutral-400" />
                            <span>Empty Password</span>
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 text-xs text-neutral-400">
                        {item.username ? (
                          <HighlightMatch
                            text={item.username}
                            query={query}
                            className="truncate max-w-[200px] font-mono"
                          />
                        ) : (
                          <span className="italic text-neutral-500">
                            {item.itemType === 'PAYMENT_CARD'
                              ? 'Payment Card'
                              : item.itemType === 'IDENTITY'
                              ? 'Identity Record'
                              : 'Secure Note'}
                          </span>
                        )}
                        {item.tags.length > 0 && (
                          <div className="hidden sm:flex items-center gap-1">
                            {item.tags.slice(0, 2).map((t) => (
                              <span
                                key={t.id}
                                className="text-[10px] px-1.5 py-0.5 rounded-md bg-white/10 text-neutral-300"
                              >
                                <HighlightMatch text={t.name} query={query} />
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Card Actions */}
                  <div className="flex items-center gap-1.5 shrink-0" onClick={(e) => e.stopPropagation()}>
                    {item.username && (
                      <button
                        type="button"
                        title="Copy Username"
                        onClick={(e) => handleCopyUsername(e, item.username, item.id)}
                        className="p-2 rounded-lg bg-white/5 hover:bg-white/15 text-neutral-300 hover:text-white transition-colors cursor-pointer"
                      >
                        {copiedId === `user_${item.id}` ? (
                          <Check className="w-3.5 h-3.5 text-emerald-400" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>
                    )}
                    {item.itemType === 'LOGIN' && (
                      <button
                        type="button"
                        title="Copy Password"
                        onClick={(e) => handleCopyPassword(e, item.id)}
                        className="p-2 rounded-lg bg-white/5 hover:bg-white/15 text-neutral-300 hover:text-white transition-colors cursor-pointer"
                      >
                        {copiedId === `pw_${item.id}` ? (
                          <Check className="w-3.5 h-3.5 text-emerald-400" />
                        ) : (
                          <Key className="w-3.5 h-3.5" />
                        )}
                      </button>
                    )}
                    <div className="text-neutral-500 group-hover:text-white transition-colors pl-1">
                      <ChevronRight className="w-4 h-4" />
                    </div>
                  </div>
                </LiquidGlassCard>
              );
            })
          )}
        </div>
      </main>

      {/* Floating Add Menu & Button */}
      <div className="fixed bottom-6 right-6 z-40">
        {showAddMenu && (
          <div className="absolute bottom-16 right-0 w-52 mb-2 p-1.5 rounded-2xl bg-black/95 border border-white/20 shadow-2xl backdrop-blur-xl space-y-1">
            {[
              { type: 'LOGIN' as VaultItemType, label: 'Login Credential', icon: Key },
              { type: 'PAYMENT_CARD' as VaultItemType, label: 'Payment Card', icon: CreditCard },
              { type: 'IDENTITY' as VaultItemType, label: 'Identity Record', icon: UserCheck },
              { type: 'SECURE_NOTE' as VaultItemType, label: 'Secure Note', icon: FileText },
            ].map((entry) => {
              const Icon = entry.icon;
              return (
                <button
                  key={entry.type}
                  type="button"
                  onClick={() => {
                    setShowAddMenu(false);
                    onAddNewClick(entry.type);
                  }}
                  className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-medium text-neutral-200 hover:text-white hover:bg-white/10 transition-colors text-left cursor-pointer"
                >
                  <Icon className="w-4 h-4 text-white" />
                  <span>{entry.label}</span>
                </button>
              );
            })}
          </div>
        )}

        <div className="relative">
          <button
            type="button"
            onClick={() => setShowAddMenu(!showAddMenu)}
            aria-label="Add item (Cmd+N)"
            title="Create new entry (⌘N)"
            className="w-14 h-14 rounded-full bg-white text-black shadow-2xl flex items-center justify-center hover:scale-105 active:scale-95 transition-all cursor-pointer font-bold"
          >
            <Plus className={`w-6 h-6 transition-transform duration-200 ${showAddMenu ? 'rotate-45' : ''}`} />
          </button>
          <span className="hidden sm:block absolute -top-1 -left-2 px-1.5 py-0.5 rounded-md bg-neutral-900 border border-white/20 text-[9px] font-mono text-neutral-300 pointer-events-none shadow-md">
            ⌘N
          </span>
        </div>
      </div>
    </div>
  );
};
