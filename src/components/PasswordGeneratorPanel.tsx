import React, { useState, useEffect } from 'react';
import { GeneratorOptions } from '../types';
import { PasswordGenerator } from '../password/passwordGenerator';
import { EntropyMeter } from './EntropyMeter';
import { Copy, RefreshCw, Check } from 'lucide-react';
import { TrustLedger } from '../trust/trustLedger';

interface PasswordGeneratorPanelProps {
  onSelectPassword?: (pw: string) => void;
  initialLength?: number;
  className?: string;
  id?: string;
}

export const PasswordGeneratorPanel: React.FC<PasswordGeneratorPanelProps> = ({
  onSelectPassword,
  initialLength = 20,
  className = '',
  id,
}) => {
  const [options, setOptions] = useState<GeneratorOptions>({
    length: initialLength,
    lower: true,
    upper: true,
    digits: true,
    symbols: true,
    avoidAmbiguous: false,
  });

  const [password, setPassword] = useState('');
  const [copied, setCopied] = useState(false);

  const generate = () => {
    try {
      const pw = PasswordGenerator.generatePassword(options);
      setPassword(pw);
      TrustLedger.record('PASSWORD_GENERATED', null, null, null, 'generator', 'success');
    } catch {
      // keep previous if invalid
    }
  };

  useEffect(() => {
    generate();
  }, [options]);

  const copyToClipboard = async () => {
    if (!password) return;
    await navigator.clipboard.writeText(password);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const entropy = PasswordGenerator.entropyBits(options);

  return (
    <div id={id} className={`liquid-glass rounded-2xl p-5 space-y-4 border border-white/20 ${className}`}>
      {/* Generated Display */}
      <div className="flex items-center justify-between gap-3 bg-white/5 border border-white/10 rounded-xl p-3.5">
        <span className="font-mono text-base sm:text-lg break-all select-all font-medium text-white tracking-wider">
          {password || 'Select character sets'}
        </span>
        <div className="flex items-center gap-1 shrink-0">
          <button
            type="button"
            onClick={generate}
            title="Regenerate"
            className="p-2 hover:bg-white/10 rounded-lg text-neutral-300 hover:text-white transition-colors cursor-pointer"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={copyToClipboard}
            title="Copy password"
            className="p-2 hover:bg-white/10 rounded-lg text-neutral-300 hover:text-white transition-colors cursor-pointer"
          >
            {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
          </button>
        </div>
      </div>

      <EntropyMeter entropy={entropy} />

      {/* Length Slider */}
      <div className="space-y-1.5 pt-1">
        <div className="flex justify-between text-xs text-neutral-300">
          <span>Length</span>
          <span className="font-mono font-bold text-white text-sm">{options.length}</span>
        </div>
        <input
          type="range"
          min="8"
          max="64"
          value={options.length}
          onChange={(e) => setOptions({ ...options, length: parseInt(e.target.value, 10) })}
          className="w-full accent-white h-1.5 bg-white/10 rounded-lg appearance-none cursor-pointer"
        />
        <div className="flex justify-between text-[10px] text-neutral-500 font-mono">
          <span>8</span>
          <span>24</span>
          <span>40</span>
          <span>64</span>
        </div>
      </div>

      {/* Character Type Toggles */}
      <div className="grid grid-cols-2 gap-2.5 pt-1">
        {[
          { key: 'lower', label: 'Lowercase (a-z)' },
          { key: 'upper', label: 'Uppercase (A-Z)' },
          { key: 'digits', label: 'Numbers (0-9)' },
          { key: 'symbols', label: 'Symbols (!@#$)' },
        ].map((item) => (
          <label
            key={item.key}
            className="flex items-center gap-2.5 text-xs text-neutral-300 bg-white/5 hover:bg-white/10 border border-white/10 rounded-xl p-2.5 cursor-pointer transition-colors"
          >
            <input
              type="checkbox"
              checked={options[item.key as keyof GeneratorOptions] as boolean}
              onChange={(e) =>
                setOptions({
                  ...options,
                  [item.key]: e.target.checked,
                })
              }
              className="accent-white rounded w-4 h-4"
            />
            <span>{item.label}</span>
          </label>
        ))}
      </div>

      <label className="flex items-center gap-2.5 text-xs text-neutral-300 bg-white/5 hover:bg-white/10 border border-white/10 rounded-xl p-2.5 cursor-pointer transition-colors">
        <input
          type="checkbox"
          checked={options.avoidAmbiguous}
          onChange={(e) => setOptions({ ...options, avoidAmbiguous: e.target.checked })}
          className="accent-white rounded w-4 h-4"
        />
        <span>Avoid ambiguous characters (O, 0, l, 1, |, `)</span>
      </label>

      {onSelectPassword && (
        <button
          type="button"
          onClick={() => onSelectPassword(password)}
          className="w-full py-2.5 px-4 bg-white text-black font-semibold text-sm rounded-xl hover:bg-neutral-200 transition-colors cursor-pointer mt-2"
        >
          Use This Password
        </button>
      )}
    </div>
  );
};
