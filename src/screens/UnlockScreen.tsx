import React, { useState } from 'react';
import { Lock, Eye, EyeOff, Fingerprint, AlertCircle } from 'lucide-react';
import { LiquidGlassCard } from '../components/LiquidGlassCard';

interface UnlockScreenProps {
  onUnlockWithPassword: (password: string) => Promise<boolean>;
  onUnlockWithBiometric?: () => Promise<boolean>;
  biometricArmed: boolean;
  onResetVault: () => void;
  error?: string | null;
  busy?: boolean;
}

export const UnlockScreen: React.FC<UnlockScreenProps> = ({
  onUnlockWithPassword,
  onUnlockWithBiometric,
  biometricArmed,
  onResetVault,
  error,
  busy = false,
}) => {
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!password) return;
    await onUnlockWithPassword(password);
  };

  return (
    <div className="min-h-screen flex flex-col justify-center items-center p-4 sm:p-6 bg-black text-white relative overflow-hidden">
      {/* Sliding Curtain Overlay */}
      <div className="curtain-security-sweep flex flex-col items-center justify-center">
        <div className="flex items-center gap-2 px-4 py-2 rounded-full bg-black/80 border border-rose-500/40 text-rose-300 font-mono text-xs shadow-2xl backdrop-blur-md">
          <Lock className="w-3.5 h-3.5 text-rose-400" />
          <span>Vault Sealed • Memory Cleared</span>
        </div>
      </div>

      <div className="w-full max-w-md space-y-6 curtain-slide-down relative z-10">
        {/* Header Branding */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-3xl bg-white/10 border border-white/20 shadow-2xl mb-2">
            <Lock className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Vault Locked</h1>
          <p className="text-sm text-neutral-400">
            Enter your master password to unwrap the Data Encryption Key
          </p>
        </div>

        {/* Unlock Card */}
        <LiquidGlassCard variant="card" className="space-y-4">
          {error && (
            <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-neutral-300">Master Password</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter master password"
                  autoFocus
                  required
                  className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-3 text-sm text-white placeholder-neutral-500 pr-10 outline-none transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-white cursor-pointer"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={busy || !password}
              className="w-full py-3 px-4 bg-white hover:bg-neutral-200 text-black font-semibold text-sm rounded-xl transition-all shadow-lg active:scale-[0.99] cursor-pointer flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {busy ? <span>Verifying & Decrypting...</span> : <span>Unlock Vault</span>}
            </button>

            {biometricArmed && onUnlockWithBiometric && (
              <button
                type="button"
                onClick={onUnlockWithBiometric}
                disabled={busy}
                className="w-full py-2.5 px-4 bg-white/10 hover:bg-white/15 text-white font-medium text-xs rounded-xl transition-all border border-white/15 flex items-center justify-center gap-2 cursor-pointer"
              >
                <Fingerprint className="w-4 h-4 text-emerald-400" />
                <span>Quick Biometric Unlock</span>
              </button>
            )}
          </form>

          <div className="pt-2 border-t border-white/10 flex items-center justify-between text-xs text-neutral-500">
            <button
              type="button"
              onClick={() => setShowResetConfirm(true)}
              className="hover:text-rose-400 transition-colors cursor-pointer"
            >
              Forgot master password?
            </button>
            <span className="font-mono text-[11px]">Zero-Knowledge</span>
          </div>
        </LiquidGlassCard>

        {showResetConfirm && (
          <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <LiquidGlassCard className="max-w-sm w-full space-y-4 border-rose-500/30">
              <div className="flex items-center gap-3 text-rose-400">
                <AlertCircle className="w-6 h-6 shrink-0" />
                <h3 className="font-bold text-base text-white">Reset Vault?</h3>
              </div>
              <p className="text-xs text-neutral-300 leading-relaxed">
                Because AtomicVault is zero-knowledge, your master password cannot be recovered.
                Resetting will delete all encrypted credentials from this device.
              </p>
              <div className="flex gap-2 justify-end pt-2">
                <button
                  type="button"
                  onClick={() => setShowResetConfirm(false)}
                  className="px-3.5 py-2 text-xs rounded-xl bg-white/10 hover:bg-white/15 text-neutral-300 font-medium cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowResetConfirm(false);
                    onResetVault();
                  }}
                  className="px-3.5 py-2 text-xs rounded-xl bg-rose-500 hover:bg-rose-600 text-white font-semibold cursor-pointer"
                >
                  Confirm Reset
                </button>
              </div>
            </LiquidGlassCard>
          </div>
        )}
      </div>
    </div>
  );
};
