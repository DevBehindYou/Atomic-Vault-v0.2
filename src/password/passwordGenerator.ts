import { GeneratorOptions, Strength } from '../types';

const LOWER = 'abcdefghijklmnopqrstuvwxyz';
const UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const DIGITS = '0123456789';
const SYMBOLS = '!@#$%^&*()-_=+[]{};:,.<>?';
const AMBIGUOUS = new Set(['O', '0', 'o', 'I', 'l', '1', '|', '`']);

export class PasswordGenerator {
  static buildPool(opts: GeneratorOptions): string {
    let pool = '';
    if (opts.lower) pool += LOWER;
    if (opts.upper) pool += UPPER;
    if (opts.digits) pool += DIGITS;
    if (opts.symbols) pool += SYMBOLS;
    if (opts.avoidAmbiguous) {
      pool = pool
        .split('')
        .filter((c) => !AMBIGUOUS.has(c))
        .join('');
    }
    return pool;
  }

  /**
   * Unbiased index in [0, max) via rejection sampling.
   * Prevents modulo bias that would otherwise compromise entropy.
   */
  static randomIndex(max: number): number {
    if (max <= 0) throw new Error('max must be positive');
    const limit = 256 - (256 % max);
    const buf = new Uint8Array(1);
    while (true) {
      crypto.getRandomValues(buf);
      const b = buf[0];
      if (b < limit) {
        return b % max;
      }
    }
  }

  static generatePassword(opts: GeneratorOptions): string {
    const pool = this.buildPool(opts);
    if (!pool.length) {
      throw new Error('Select at least one character type');
    }
    if (opts.length <= 0) {
      throw new Error('Length must be positive');
    }
    let res = '';
    for (let i = 0; i < opts.length; i++) {
      res += pool[this.randomIndex(pool.length)];
    }
    return res;
  }

  static entropyBits(opts: GeneratorOptions): number {
    const poolSize = this.buildPool(opts).length;
    if (poolSize === 0) return 0;
    return opts.length * (Math.log(poolSize) / Math.log(2.0));
  }

  static strengthFromEntropy(bits: number): Strength {
    if (bits < 40) return 'WEAK';
    if (bits < 60) return 'FAIR';
    if (bits < 80) return 'STRONG';
    return 'EXCELLENT';
  }
}
