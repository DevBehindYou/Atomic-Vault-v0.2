import React, { useState } from 'react';
import {
  ArrowLeft,
  ShieldCheck,
  CheckCircle2,
  Lock,
  WifiOff,
  Database,
  History,
  AlertTriangle,
  RefreshCw,
  Cpu,
} from 'lucide-react';
import { LiquidGlassCard } from '../components/LiquidGlassCard';
import { TrustLedger } from '../trust/trustLedger';

interface PrivacyProofScreenProps {
  onBack: () => void;
  onViewTimeline: () => void;
}

export const PrivacyProofScreen: React.FC<PrivacyProofScreenProps> = ({
  onBack,
  onViewTimeline,
}) => {
  const [verifying, setVerifying] = useState(false);
  const [verificationResult, setVerificationResult] = useState<string | null>(null);
  const [entriesCount, setEntriesCount] = useState<number>(() => TrustLedger.listEntries(1000).length);

  const handleVerifyChain = async () => {
    setVerifying(true);
    setVerificationResult(null);
    try {
      const brokenId = await TrustLedger.verifyChainIntegrity();
      const count = TrustLedger.listEntries(1000).length;
      setEntriesCount(count);
      if (!brokenId) {
        setVerificationResult(`VERIFIED: All ${count} ledger events cryptographically intact.`);
        await TrustLedger.record('INTEGRITY_CHECK_COMPLETED', null, null, null, 'privacy_proof', 'success');
      } else {
        setVerificationResult(`TAMPER DETECTED at event ID: ${brokenId}`);
      }
    } catch {
      setVerificationResult('Verification encountered an error.');
    } finally {
      setVerifying(false);
    }
  };

  const privacyGuarantees = [
    {
      icon: WifiOff,
      title: 'Zero Network Outbound Calls',
      detail: 'AtomicVault does not transmit any network packets. No telemetry, no metrics, no third-party trackers.',
      status: 'Enforced',
    },
    {
      icon: Lock,
      title: 'Envelope Encryption (AES-256-GCM)',
      detail: 'Data Encryption Key (DEK) is generated locally via CSPRNG and wrapped by KEK (PBKDF2 SHA-256, 100,000 iterations).',
      status: 'Active',
    },
    {
      icon: Database,
      title: 'Isolated Client-Side Storage',
      detail: 'All credential payloads and encrypted custom fields reside exclusively in browser-isolated client storage.',
      status: 'Active',
    },
    {
      icon: History,
      title: 'Tamper-Evident Audit Ledger',
      detail: 'Security events (unlocks, fills, edits, exports) are signed into an HMAC-SHA256 hash-chain rooted in GENESIS.',
      status: `${entriesCount} Events`,
    },
    {
      icon: Cpu,
      title: 'Unbiased CSPRNG Password Generation',
      detail: 'Character selection uses rejection sampling to eliminate modulo bias and maximize raw Shannon entropy.',
      status: 'CSPRNG',
    },
  ];

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
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <h2 className="font-bold text-base sm:text-lg text-white">Trust & Privacy Proof</h2>
          </div>
        </div>

        <button
          type="button"
          onClick={onViewTimeline}
          className="text-xs text-neutral-300 hover:text-white px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 transition-colors cursor-pointer"
        >
          View Audit Log
        </button>
      </header>

      <main className="max-w-2xl mx-auto px-4 sm:px-6 pt-6 space-y-6">
        {/* Verification Card */}
        <LiquidGlassCard variant="glow" className="space-y-4 border-emerald-500/30">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="font-bold text-base text-white flex items-center gap-2">
                <ShieldCheck className="w-5 h-5 text-emerald-400" />
                Cryptographic Ledger Integrity
              </h3>
              <p className="text-xs text-neutral-400 mt-1">
                Walks every entry in the hash-chain, re-computes HMAC signatures, and checks parent links.
              </p>
            </div>
            <button
              type="button"
              onClick={handleVerifyChain}
              disabled={verifying}
              className="px-3.5 py-2 bg-emerald-500 hover:bg-emerald-600 text-black font-bold text-xs rounded-xl transition-all shadow-md shrink-0 flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${verifying ? 'animate-spin' : ''}`} />
              <span>{verifying ? 'Verifying...' : 'Verify Chain'}</span>
            </button>
          </div>

          {verificationResult && (
            <div
              className={`p-3 rounded-xl text-xs font-mono flex items-center gap-2 ${
                verificationResult.startsWith('VERIFIED')
                  ? 'bg-emerald-500/10 border border-emerald-500/30 text-emerald-400'
                  : 'bg-rose-500/10 border border-rose-500/30 text-rose-400'
              }`}
            >
              {verificationResult.startsWith('VERIFIED') ? (
                <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
              ) : (
                <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
              )}
              <span>{verificationResult}</span>
            </div>
          )}
        </LiquidGlassCard>

        {/* Core Guarantees List */}
        <div className="space-y-3">
          <h3 className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">
            Architecture Guarantees
          </h3>

          <div className="space-y-2.5">
            {privacyGuarantees.map((item, idx) => {
              const Icon = item.icon;
              return (
                <LiquidGlassCard key={idx} className="flex items-start gap-3.5 p-4">
                  <div className="w-9 h-9 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center shrink-0 mt-0.5">
                    <Icon className="w-4 h-4 text-emerald-400" />
                  </div>
                  <div className="flex-1 space-y-1">
                    <div className="flex items-center justify-between">
                      <h4 className="font-semibold text-sm text-white">{item.title}</h4>
                      <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-white/10 text-neutral-300">
                        {item.status}
                      </span>
                    </div>
                    <p className="text-xs text-neutral-400 leading-relaxed">{item.detail}</p>
                  </div>
                </LiquidGlassCard>
              );
            })}
          </div>
        </div>
      </main>
    </div>
  );
};
