import React, { useState, useEffect } from 'react';
import { Copy, Check, Clock } from 'lucide-react';

interface TotpDisplayProps {
  secret: string;
}

export const TotpDisplay: React.FC<TotpDisplayProps> = ({ secret }) => {
  const [code, setCode] = useState<string>('------');
  const [secondsRemaining, setSecondsRemaining] = useState<number>(30);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!secret || !secret.trim()) return;

    let isMounted = true;

    // Helper: Base32 decode
    const base32Decode = (base32: string): Uint8Array | null => {
      const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
      const clean = base32.toUpperCase().replace(/[^A-Z2-7]/g, '');
      let bits = 0;
      let value = 0;
      const output = [];

      for (let i = 0; i < clean.length; i++) {
        const idx = alphabet.indexOf(clean[i]);
        if (idx === -1) return null;
        value = (value << 5) | idx;
        bits += 5;
        if (bits >= 8) {
          output.push((value >>> (bits - 8)) & 255);
          bits -= 8;
        }
      }
      return new Uint8Array(output);
    };

    const updateTotp = async () => {
      try {
        const epoch = Math.floor(Date.now() / 1000);
        const timeStep = 30;
        const counter = Math.floor(epoch / timeStep);
        const remaining = timeStep - (epoch % timeStep);
        if (isMounted) setSecondsRemaining(remaining);

        const keyBytes = base32Decode(secret);
        if (!keyBytes || keyBytes.length === 0) {
          if (isMounted) setCode('INVALID');
          return;
        }

        // 8-byte big-endian counter
        const counterBuffer = new ArrayBuffer(8);
        const counterView = new DataView(counterBuffer);
        counterView.setUint32(4, counter, false);

        const cryptoKey = await crypto.subtle.importKey(
          'raw',
          keyBytes as BufferSource,
          { name: 'HMAC', hash: 'SHA-1' },
          false,
          ['sign']
        );

        const hmac = await crypto.subtle.sign('HMAC', cryptoKey, counterBuffer);
        const hmacBytes = new Uint8Array(hmac);
        const offset = hmacBytes[hmacBytes.length - 1] & 0x0f;
        const binary =
          ((hmacBytes[offset] & 0x7f) << 24) |
          ((hmacBytes[offset + 1] & 0xff) << 16) |
          ((hmacBytes[offset + 2] & 0xff) << 8) |
          (hmacBytes[offset + 3] & 0xff);

        const otp = (binary % 1000000).toString().padStart(6, '0');
        if (isMounted) setCode(otp);
      } catch {
        if (isMounted) setCode('ERROR');
      }
    };

    updateTotp();
    const interval = setInterval(updateTotp, 1000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [secret]);

  if (!secret || !secret.trim()) return null;

  const copyCode = async () => {
    if (code && code !== '------' && code !== 'INVALID' && code !== 'ERROR') {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const formattedCode = `${code.slice(0, 3)} ${code.slice(3)}`;

  return (
    <div className="flex items-center justify-between bg-white/5 border border-white/10 rounded-xl p-3">
      <div className="flex items-center gap-3">
        <Clock className="w-4 h-4 text-neutral-400" />
        <div>
          <div className="text-[10px] uppercase tracking-wider text-neutral-400 font-semibold">
            One-Time Password (TOTP)
          </div>
          <div className="font-mono text-xl font-bold tracking-widest text-emerald-400">
            {formattedCode}
          </div>
        </div>
      </div>
      <div className="flex items-center gap-3">
        <div className="text-right">
          <span className="text-xs font-mono text-neutral-400">{secondsRemaining}s</span>
        </div>
        <button
          type="button"
          onClick={copyCode}
          className="p-2 hover:bg-white/10 rounded-lg text-neutral-300 hover:text-white transition-colors cursor-pointer"
          title="Copy TOTP Code"
        >
          {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
        </button>
      </div>
    </div>
  );
};
