import React from 'react';
import { PasswordGenerator } from '../password/passwordGenerator';
import { Strength } from '../types';

interface EntropyMeterProps {
  entropy: number;
  className?: string;
}

export const EntropyMeter: React.FC<EntropyMeterProps> = ({ entropy, className = '' }) => {
  const strength: Strength = PasswordGenerator.strengthFromEntropy(entropy);
  const percentage = Math.min(100, Math.round((entropy / 80) * 100));

  const getColor = () => {
    switch (strength) {
      case 'WEAK':
        return 'bg-rose-500 text-rose-400';
      case 'FAIR':
        return 'bg-amber-500 text-amber-400';
      case 'STRONG':
        return 'bg-emerald-500 text-emerald-400';
      case 'EXCELLENT':
        return 'bg-cyan-400 text-cyan-300';
    }
  };

  return (
    <div className={`space-y-1.5 ${className}`}>
      <div className="flex items-center justify-between text-xs font-mono">
        <span className="text-neutral-400">Entropy: {Math.round(entropy)} bits</span>
        <span className={`font-semibold tracking-wider ${getColor().split(' ')[1]}`}>
          {strength}
        </span>
      </div>
      <div className="h-1.5 w-full bg-white/10 rounded-full overflow-hidden">
        <div
          className={`h-full transition-all duration-300 rounded-full ${getColor().split(' ')[0]}`}
          style={{ width: `${Math.max(5, percentage)}%` }}
        />
      </div>
    </div>
  );
};
