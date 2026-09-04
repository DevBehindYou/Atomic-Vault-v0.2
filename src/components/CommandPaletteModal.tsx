import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Search,
  Key,
  CreditCard,
  UserCheck,
  FileText,
  Folder,
  Tag,
  ShieldAlert,
  ShieldCheck,
  Settings,
  Lock,
  Plus,
  Sparkles,
  ArrowRight,
  X,
} from 'lucide-react';
import { CredentialPlain, FolderPlain, TagPlain, VaultItemType, CredentialFinding } from '../types';

export interface CommandPaletteModalProps {
  isOpen: boolean;
  onClose: () => void;
  vaultItems: CredentialPlain[];
  folders: FolderPlain[];
  tags: TagPlain[];
  findings?: CredentialFinding[];
  onSelectItem: (id: string, type: VaultItemType) => void;
  onSelectFolder: (folderId: string | null) => void;
  onSelectTag: (tagId: string | null) => void;
  onAddNew: (type: VaultItemType) => void;
  onNavigate: (route: string) => void;
  onLock: () => void;
}

interface PaletteItem {
  id: string;
  category: 'ITEM' | 'FOLDER' | 'TAG' | 'ACTION';
  title: string;
  subtitle?: string;
  icon: React.ReactNode;
  badge?: string;
  badgeType?: 'warning' | 'danger' | 'info' | 'neutral';
  colorDot?: string | null;
  onExecute: () => void;
}

export const CommandPaletteModal: React.FC<CommandPaletteModalProps> = ({
  isOpen,
  onClose,
  vaultItems,
  folders,
  tags,
  findings = [],
  onSelectItem,
  onSelectFolder,
  onSelectTag,
  onAddNew,
  onNavigate,
  onLock,
}) => {
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // Map weak / reused findings
  const issueMap = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const f of findings) {
      map.set(f.credential.id, f.issues);
    }
    return map;
  }, [findings]);

  // Reset query and focus on open
  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setActiveIndex(0);
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
    }
  }, [isOpen]);

  // Build filtered items
  const paletteItems: PaletteItem[] = useMemo(() => {
    const q = query.toLowerCase().trim();
    const items: PaletteItem[] = [];

    // 1. Vault Items matching search
    const matchingVaultItems = vaultItems.filter((i) => {
      if (!q) return true;
      return (
        i.title.toLowerCase().includes(q) ||
        i.username.toLowerCase().includes(q) ||
        (i.uriMatchPattern && i.uriMatchPattern.toLowerCase().includes(q)) ||
        (i.notes && i.notes.toLowerCase().includes(q))
      );
    });

    // Take top 8 matching items
    for (const item of matchingVaultItems.slice(0, 8)) {
      const issues = issueMap.get(item.id);
      let badge: string | undefined;
      let badgeType: 'warning' | 'danger' | 'info' | 'neutral' | undefined;

      if (issues?.includes('REUSED')) {
        badge = 'Reused';
        badgeType = 'danger';
      } else if (issues?.includes('WEAK')) {
        badge = 'Weak';
        badgeType = 'warning';
      }

      let icon = <Key className="w-4 h-4 text-emerald-400" />;
      if (item.itemType === 'PAYMENT_CARD') icon = <CreditCard className="w-4 h-4 text-cyan-400" />;
      else if (item.itemType === 'IDENTITY') icon = <UserCheck className="w-4 h-4 text-purple-400" />;
      else if (item.itemType === 'SECURE_NOTE') icon = <FileText className="w-4 h-4 text-amber-400" />;

      items.push({
        id: `item_${item.id}`,
        category: 'ITEM',
        title: item.title,
        subtitle: item.username || item.uriMatchPattern || (item.itemType === 'SECURE_NOTE' ? 'Secure Note' : 'Vault Item'),
        icon,
        badge,
        badgeType,
        onExecute: () => {
          onSelectItem(item.id, item.itemType);
          onClose();
        },
      });
    }

    // 2. Folders
    const matchingFolders = folders.filter((f) => !q || f.name.toLowerCase().includes(q));
    for (const folder of matchingFolders) {
      items.push({
        id: `folder_${folder.id}`,
        category: 'FOLDER',
        title: folder.name,
        subtitle: 'Filter by Folder',
        icon: <Folder className="w-4 h-4 text-blue-400" />,
        onExecute: () => {
          onSelectFolder(folder.id);
          onClose();
        },
      });
    }

    // 3. Tags
    const matchingTags = tags.filter((t) => !q || t.name.toLowerCase().includes(q));
    for (const tag of matchingTags) {
      items.push({
        id: `tag_${tag.id}`,
        category: 'TAG',
        title: tag.name,
        subtitle: 'Filter by Tag',
        colorDot: tag.color,
        icon: <Tag className="w-4 h-4 text-neutral-400" />,
        onExecute: () => {
          onSelectTag(tag.id);
          onClose();
        },
      });
    }

    // 4. Quick Actions / Navigation
    const allActions: PaletteItem[] = [
      {
        id: 'action_new_login',
        category: 'ACTION',
        title: 'Create New Login',
        subtitle: 'Add password credential',
        icon: <Plus className="w-4 h-4 text-emerald-400" />,
        onExecute: () => {
          onAddNew('LOGIN');
          onClose();
        },
      },
      {
        id: 'action_new_card',
        category: 'ACTION',
        title: 'Create Payment Card',
        subtitle: 'Store credit/debit card',
        icon: <Plus className="w-4 h-4 text-cyan-400" />,
        onExecute: () => {
          onAddNew('PAYMENT_CARD');
          onClose();
        },
      },
      {
        id: 'action_new_note',
        category: 'ACTION',
        title: 'Create Secure Note',
        subtitle: 'Encrypted scratchpad',
        icon: <Plus className="w-4 h-4 text-amber-400" />,
        onExecute: () => {
          onAddNew('SECURE_NOTE');
          onClose();
        },
      },
      {
        id: 'action_generator',
        category: 'ACTION',
        title: 'Password Generator',
        subtitle: 'Generate high-entropy credentials',
        icon: <Sparkles className="w-4 h-4 text-white" />,
        onExecute: () => {
          onNavigate('PASSWORD_GENERATOR');
          onClose();
        },
      },
      {
        id: 'action_security',
        category: 'ACTION',
        title: 'Security & Password Health Audit',
        subtitle: 'Examine weak and reused credentials',
        icon: <ShieldAlert className="w-4 h-4 text-amber-400" />,
        onExecute: () => {
          onNavigate('SECURITY_DASHBOARD');
          onClose();
        },
      },
      {
        id: 'action_privacy',
        category: 'ACTION',
        title: 'Privacy Proof & Verification',
        subtitle: 'Inspect zero-knowledge cryptographic status',
        icon: <ShieldCheck className="w-4 h-4 text-emerald-400" />,
        onExecute: () => {
          onNavigate('PRIVACY_PROOF');
          onClose();
        },
      },
      {
        id: 'action_settings',
        category: 'ACTION',
        title: 'Settings & Data Backup',
        subtitle: 'Manage auto-lock, folders, and export',
        icon: <Settings className="w-4 h-4 text-neutral-300" />,
        onExecute: () => {
          onNavigate('SETTINGS');
          onClose();
        },
      },
      {
        id: 'action_lock',
        category: 'ACTION',
        title: 'Lock Vault Immediately',
        subtitle: 'Wipe keys from memory (Cmd+L)',
        icon: <Lock className="w-4 h-4 text-rose-400" />,
        onExecute: () => {
          onLock();
          onClose();
        },
      },
    ];

    const matchingActions = allActions.filter((a) => {
      if (!q) return true;
      return a.title.toLowerCase().includes(q) || (a.subtitle && a.subtitle.toLowerCase().includes(q));
    });

    items.push(...matchingActions);

    return items;
  }, [query, vaultItems, folders, tags, issueMap, onSelectItem, onSelectFolder, onSelectTag, onAddNew, onNavigate, onLock, onClose]);

  // Keep active index in bounds
  useEffect(() => {
    setActiveIndex((prev) => {
      if (paletteItems.length === 0) return 0;
      return Math.min(prev, paletteItems.length - 1);
    });
  }, [paletteItems]);

  // Scroll active item into view
  useEffect(() => {
    const activeEl = listRef.current?.querySelector(`[data-index="${activeIndex}"]`);
    activeEl?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex]);

  // Global key navigation inside modal
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((prev) => (paletteItems.length > 0 ? (prev + 1) % paletteItems.length : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((prev) => (paletteItems.length > 0 ? (prev - 1 + paletteItems.length) % paletteItems.length : 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (paletteItems[activeIndex]) {
        paletteItems[activeIndex].onExecute();
      }
    }
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-start justify-center pt-16 sm:pt-24 px-4 sm:px-6"
      onClick={onClose}
      onKeyDown={handleKeyDown}
    >
      <div
        className="w-full max-w-xl bg-neutral-950 border border-white/20 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[75vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search Input Bar */}
        <div className="relative border-b border-white/10 px-4 py-3.5 flex items-center gap-3">
          <Search className="w-5 h-5 text-neutral-400 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setActiveIndex(0);
            }}
            placeholder="Type a command, folder, tag, or search credentials..."
            className="w-full bg-transparent text-sm text-white placeholder-neutral-500 outline-none pr-8"
          />
          {query ? (
            <button
              type="button"
              onClick={() => setQuery('')}
              className="p-1 rounded-md text-neutral-400 hover:text-white cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          ) : (
            <kbd className="hidden sm:inline-block text-[10px] font-mono px-1.5 py-0.5 rounded bg-white/10 text-neutral-400 border border-white/10">
              ESC
            </kbd>
          )}
        </div>

        {/* Results List */}
        <div ref={listRef} className="overflow-y-auto p-2 space-y-1 divide-y divide-white/5 no-scrollbar flex-1">
          {paletteItems.length === 0 ? (
            <div className="py-12 text-center text-xs text-neutral-400 space-y-1">
              <Search className="w-6 h-6 mx-auto text-neutral-600 mb-2" />
              <div className="font-semibold text-neutral-300">No matching commands or vault items</div>
              <p className="text-[11px] text-neutral-500">Try searching by title, username, folder, or tag name.</p>
            </div>
          ) : (
            paletteItems.map((item, idx) => {
              const isActive = idx === activeIndex;
              return (
                <div
                  key={item.id}
                  data-index={idx}
                  onClick={item.onExecute}
                  onMouseEnter={() => setActiveIndex(idx)}
                  className={`flex items-center justify-between gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-colors ${
                    isActive ? 'bg-white/15 text-white' : 'text-neutral-300 hover:bg-white/5'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-8 h-8 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center shrink-0">
                      {item.icon}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        {item.colorDot && (
                          <span
                            className="w-2 h-2 rounded-full shrink-0"
                            style={{ backgroundColor: item.colorDot }}
                          />
                        )}
                        <span className="font-medium text-xs truncate text-white">{item.title}</span>
                        {item.badge && (
                          <span
                            className={`text-[9px] font-semibold px-1.5 py-0.5 rounded ${
                              item.badgeType === 'danger'
                                ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                                : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                            }`}
                          >
                            {item.badge}
                          </span>
                        )}
                      </div>
                      {item.subtitle && (
                        <div className="text-[11px] text-neutral-400 truncate">{item.subtitle}</div>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0 text-neutral-500">
                    <span className="text-[10px] uppercase font-mono tracking-wider opacity-60">
                      {item.category}
                    </span>
                    {isActive && <ArrowRight className="w-3.5 h-3.5 text-white ml-1" />}
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer shortcuts */}
        <div className="border-t border-white/10 px-4 py-2.5 bg-black/40 flex items-center justify-between text-[11px] text-neutral-400">
          <div className="flex items-center gap-3">
            <span className="flex items-center gap-1">
              <kbd className="font-mono text-[9px] bg-white/10 px-1 py-0.5 rounded text-neutral-300">↑</kbd>
              <kbd className="font-mono text-[9px] bg-white/10 px-1 py-0.5 rounded text-neutral-300">↓</kbd>
              <span>to navigate</span>
            </span>
            <span className="flex items-center gap-1">
              <kbd className="font-mono text-[9px] bg-white/10 px-1 py-0.5 rounded text-neutral-300">↵</kbd>
              <span>to select</span>
            </span>
          </div>
          <span className="flex items-center gap-1">
            <kbd className="font-mono text-[9px] bg-white/10 px-1 py-0.5 rounded text-neutral-300">ESC</kbd>
            <span>to close</span>
          </span>
        </div>
      </div>
    </div>
  );
};
