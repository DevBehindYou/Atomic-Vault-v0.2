import React from 'react';
import { ArrowLeft, Sparkles } from 'lucide-react';
import { PasswordGeneratorPanel } from '../components/PasswordGeneratorPanel';

interface PasswordGeneratorScreenProps {
  onBack: () => void;
}

export const PasswordGeneratorScreen: React.FC<PasswordGeneratorScreenProps> = ({ onBack }) => {
  return (
    <div className="min-h-screen bg-black text-white pb-20">
      <header className="sticky top-0 z-20 bg-black/80 backdrop-blur-md border-b border-white/10 px-4 sm:px-8 py-3.5 flex items-center gap-3">
        <button
          type="button"
          onClick={onBack}
          className="p-1.5 -ml-1 rounded-xl text-neutral-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-white" />
          <h2 className="font-bold text-base sm:text-lg text-white">Password Generator</h2>
        </div>
      </header>

      <main className="max-w-xl mx-auto px-4 sm:px-6 pt-8 space-y-4">
        <p className="text-xs text-neutral-400">
          Generated client-side using WebCrypto CSPRNG with unbiased rejection sampling.
          No entropy ever leaves your device.
        </p>

        <PasswordGeneratorPanel initialLength={24} />
      </main>
    </div>
  );
};
