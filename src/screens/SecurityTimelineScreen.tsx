import React, { useState, useEffect } from 'react';
import { ArrowLeft, History, CheckCircle2, XCircle, Trash2 } from 'lucide-react';
import { TrustLedgerEntry } from '../types';
import { TrustLedger } from '../trust/trustLedger';
import { LiquidGlassCard } from '../components/LiquidGlassCard';

interface SecurityTimelineScreenProps {
  onBack: () => void;
}

export const SecurityTimelineScreen: React.FC<SecurityTimelineScreenProps> = ({ onBack }) => {
  const [entries, setEntries] = useState<TrustLedgerEntry[]>([]);

  const loadEntries = () => {
    setEntries(TrustLedger.listEntries(100));
  };

  useEffect(() => {
    loadEntries();
  }, []);

  const handleClear = () => {
    if (confirm('Clear trust ledger audit history?')) {
      TrustLedger.clear();
      loadEntries();
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
          <div className="flex items-center gap-2">
            <History className="w-4 h-4 text-white" />
            <h2 className="font-bold text-base sm:text-lg text-white">Trust Ledger Audit Log</h2>
          </div>
        </div>

        {entries.length > 0 && (
          <button
            type="button"
            onClick={handleClear}
            className="p-2 rounded-xl text-neutral-400 hover:text-rose-400 hover:bg-white/5 transition-colors cursor-pointer"
            title="Clear Ledger"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        )}
      </header>

      <main className="max-w-3xl mx-auto px-4 sm:px-6 pt-6 space-y-4">
        <p className="text-xs text-neutral-400">
          Immutable, hash-chained chronological log of security events. Each record is verified with an
          HMAC signed with the local tamper key.
        </p>

        {entries.length === 0 ? (
          <LiquidGlassCard className="text-center py-12 space-y-2">
            <History className="w-8 h-8 text-neutral-500 mx-auto" />
            <div className="text-sm font-semibold text-neutral-300">No events recorded</div>
            <p className="text-xs text-neutral-500">Security actions will be logged here.</p>
          </LiquidGlassCard>
        ) : (
          <div className="space-y-2.5">
            {entries.map((entry) => {
              const isSuccess = entry.result === 'success';
              const dateStr = new Date(entry.timestamp).toLocaleString();

              return (
                <LiquidGlassCard key={entry.id} className="p-3.5 space-y-2 text-xs">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      {isSuccess ? (
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                      ) : (
                        <XCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                      )}
                      <span className="font-mono font-semibold text-white tracking-wide">
                        {entry.eventType}
                      </span>
                    </div>
                    <span className="text-[11px] text-neutral-400 font-mono">{dateStr}</span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-1 text-[11px] font-mono text-neutral-400 bg-white/5 p-2 rounded-lg">
                    <div>Source: <span className="text-neutral-200">{entry.source}</span></div>
                    <div>Result: <span className={isSuccess ? 'text-emerald-400' : 'text-rose-400'}>{entry.result}</span></div>
                    <div className="truncate">Prev Hash: <span className="text-neutral-300">{entry.previousHash.slice(0, 16)}...</span></div>
                    <div className="truncate">Event Hash: <span className="text-neutral-300">{entry.eventHash.slice(0, 16)}...</span></div>
                  </div>
                </LiquidGlassCard>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
};
