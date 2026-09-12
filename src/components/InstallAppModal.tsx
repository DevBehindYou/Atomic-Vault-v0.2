import React from 'react';
import { Download, X, Smartphone, Monitor, ShieldCheck, CheckCircle2 } from 'lucide-react';

interface InstallAppModalProps {
  isOpen: boolean;
  onClose: () => void;
  onInstallNative?: () => void;
  canPromptNative: boolean;
  isInstalled: boolean;
}

export const InstallAppModal: React.FC<InstallAppModalProps> = ({
  isOpen,
  onClose,
  onInstallNative,
  canPromptNative,
  isInstalled,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="relative w-full max-w-md bg-zinc-900 border border-zinc-700/80 rounded-2xl shadow-2xl p-6 text-white space-y-5">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors"
          aria-label="Close modal"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center shadow-lg shadow-emerald-500/20">
            <Download className="w-6 h-6 text-black" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-white tracking-tight">Install AtomicVault</h2>
            <p className="text-xs text-zinc-400">Offline Zero-Knowledge Password Vault</p>
          </div>
        </div>

        {isInstalled ? (
          <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center gap-3 text-emerald-400 text-sm">
            <CheckCircle2 className="w-5 h-5 shrink-0" />
            <span>AtomicVault is already installed as a standalone offline application.</span>
          </div>
        ) : canPromptNative ? (
          <div className="space-y-3">
            <p className="text-sm text-zinc-300">
              Install AtomicVault directly to your home screen or desktop application list. It runs completely offline with zero network transmission.
            </p>
            <button
              onClick={() => {
                onInstallNative?.();
                onClose();
              }}
              className="w-full py-3 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-bold text-sm flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/25 transition-all cursor-pointer"
            >
              <Download className="w-4 h-4" />
              Install Application Now
            </button>
          </div>
        ) : (
          <div className="space-y-4 text-sm text-zinc-300">
            <p>
              AtomicVault is a Progressive Web App (PWA) that installs directly to your home screen or application launcher without an app store:
            </p>

            <div className="space-y-3 text-xs bg-black/40 p-4 rounded-xl border border-zinc-800">
              <div className="flex items-start gap-2.5">
                <Smartphone className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                <div>
                  <strong className="text-white block">On Android / Mobile Chrome:</strong>
                  Tap the browser menu <span className="text-zinc-200 font-mono">(⋮)</span> in the top-right corner, then select <span className="text-emerald-400 font-semibold">"Install app"</span> or <span className="text-emerald-400 font-semibold">"Add to Home screen"</span>.
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <Smartphone className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
                <div>
                  <strong className="text-white block">On iOS (iPhone/iPad Safari):</strong>
                  Tap the <span className="text-cyan-400 font-semibold">Share</span> button at the bottom of Safari, scroll down and tap <span className="text-cyan-400 font-semibold">"Add to Home Screen"</span>.
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <Monitor className="w-4 h-4 text-purple-400 shrink-0 mt-0.5" />
                <div>
                  <strong className="text-white block">On Desktop (Chrome, Edge, Brave):</strong>
                  Click the <span className="text-purple-400 font-semibold">Install</span> icon in your browser’s URL address bar to run as a dedicated desktop app.
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 text-xs text-zinc-400 pt-1">
              <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>Once installed, AtomicVault works 100% offline with zero network calls.</span>
            </div>
          </div>
        )}

        <div className="flex justify-end pt-2">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-xs font-medium transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
