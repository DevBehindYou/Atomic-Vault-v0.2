import React, { useState } from 'react';
import { Shield, Lock, Eye, EyeOff, KeyRound, Sparkles, CheckCircle2 } from 'lucide-react';
import { LiquidGlassCard } from '../components/LiquidGlassCard';

interface OnboardingScreenProps {
  onCreateVault: (password: string, biometricEnabled: boolean, loadSamples: boolean) => Promise<void>;
  error?: string | null;
  busy?: boolean;
}

export const OnboardingScreen: React.FC<OnboardingScreenProps> = ({
  onCreateVault,
  error,
  busy = false,
}) => {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [biometricEnabled, setBiometricEnabled] = useState(true);
  const [loadSamples, setLoadSamples] = useState(true);
  const [localError, setLocalError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);

    if (password.length < 8) {
      setLocalError('Master password must be at least 8 characters long');
      return;
    }
    if (password !== confirmPassword) {
      setLocalError('Master passwords do not match');
      return;
    }

    await onCreateVault(password, biometricEnabled, loadSamples);
  };

  return (
    <div className="min-h-screen flex flex-col justify-center items-center p-4 sm:p-6 bg-black text-white relative">
      <div className="w-full max-w-md space-y-6">
        {/* Header Branding */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-3xl bg-white/10 border border-white/20 shadow-2xl mb-2">
            <Shield className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-white">AtomicVault</h1>
          <p className="text-sm text-neutral-400">
            Offline zero-knowledge credential vault with hardware-grade envelope encryption
          </p>
        </div>

        {/* Create Vault Form */}
        <LiquidGlassCard variant="card" className="space-y-5">
          <div className="border-b border-white/10 pb-3">
            <h2 className="text-base font-semibold text-white flex items-center gap-2">
              <KeyRound className="w-4 h-4 text-white" />
              Initialize Master Key
            </h2>
            <p className="text-xs text-neutral-400 mt-1">
              Your master password encrypts the Data Encryption Key (DEK). It is never sent to any server.
            </p>
          </div>

          {(localError || error) && (
            <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl">
              {localError || error}
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
                  placeholder="Min. 8 characters"
                  required
                  className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 pr-10 outline-none transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-white"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-neutral-300">Confirm Master Password</label>
              <input
                type={showPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Re-type master password"
                required
                className="w-full bg-white/5 border border-white/15 focus:border-white/40 focus:ring-1 focus:ring-white/40 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-neutral-500 outline-none transition-all"
              />
            </div>

            {/* Options */}
            <div className="space-y-2.5 pt-2 border-t border-white/10">
              <label className="flex items-center justify-between cursor-pointer p-2 rounded-xl hover:bg-white/5 transition-colors">
                <div className="flex items-center gap-2.5 text-xs">
                  <Lock className="w-4 h-4 text-neutral-300" />
                  <div>
                    <div className="font-medium text-white">Quick Unlock</div>
                    <div className="text-[11px] text-neutral-400">Unlock with simulated biometric credential</div>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={biometricEnabled}
                  onChange={(e) => setBiometricEnabled(e.target.checked)}
                  className="accent-white w-4 h-4 rounded cursor-pointer"
                />
              </label>

              <label className="flex items-center justify-between cursor-pointer p-2 rounded-xl hover:bg-white/5 transition-colors">
                <div className="flex items-center gap-2.5 text-xs">
                  <Sparkles className="w-4 h-4 text-emerald-400" />
                  <div>
                    <div className="font-medium text-white">Seed Sample Items</div>
                    <div className="text-[11px] text-neutral-400">Pre-populate with sample cards, logins & notes</div>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={loadSamples}
                  onChange={(e) => setLoadSamples(e.target.checked)}
                  className="accent-white w-4 h-4 rounded cursor-pointer"
                />
              </label>
            </div>

            <button
              type="submit"
              disabled={busy}
              className="w-full py-3 px-4 bg-white hover:bg-neutral-200 text-black font-semibold text-sm rounded-xl transition-all shadow-lg active:scale-[0.99] cursor-pointer flex items-center justify-center gap-2 mt-2 disabled:opacity-50"
            >
              {busy ? (
                <span>Deriving KEK & Initializing...</span>
              ) : (
                <>
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Create Encrypted Vault</span>
                </>
              )}
            </button>
          </form>
        </LiquidGlassCard>

        {/* Security badges */}
        <div className="flex items-center justify-center gap-4 text-xs text-neutral-500">
          <span>AES-256-GCM</span>
          <span>•</span>
          <span>Zero Knowledge</span>
          <span>•</span>
          <span>Tamper-Evident Ledger</span>
        </div>
      </div>
    </div>
  );
};
