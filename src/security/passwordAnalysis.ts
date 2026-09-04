import { CredentialFinding, CredentialPlain, PasswordIssue, VaultSecurityReport } from '../types';

export class PasswordAnalysis {
  static readonly WEAK_ENTROPY_BITS = 50.0;

  static estimateEntropyBits(password: string): number {
    if (!password) return 0.0;
    let pool = 0;
    if (/[a-z]/.test(password)) pool += 26;
    if (/[A-Z]/.test(password)) pool += 26;
    if (/[0-9]/.test(password)) pool += 10;
    if (/[^a-zA-Z0-9]/.test(password)) pool += 32;
    if (pool === 0) return 0.0;
    return password.length * (Math.log(pool) / Math.log(2.0));
  }

  static analyzeVault(items: CredentialPlain[]): VaultSecurityReport {
    // Only analyze login credentials or items with a password field
    const checkableItems = items.filter((item) => item.itemType === 'LOGIN' || item.password);

    if (checkableItems.length === 0) {
      return {
        score: 100,
        reusedCount: 0,
        weakCount: 0,
        emptyCount: 0,
        totalCount: 0,
        findings: [],
      };
    }

    const passwordCounts = new Map<string, number>();
    for (const item of checkableItems) {
      const pass = (item.password || '').trim();
      if (pass.length > 0) {
        passwordCounts.set(pass, (passwordCounts.get(pass) || 0) + 1);
      }
    }

    let emptyCount = 0;
    let reusedCount = 0;
    let weakCount = 0;
    const findings: CredentialFinding[] = [];
    const flaggedItemIds = new Set<string>();

    for (const item of checkableItems) {
      const pass = (item.password || '').trim();
      const issues: PasswordIssue[] = [];
      const entropy = this.estimateEntropyBits(pass);

      if (pass.length === 0) {
        issues.push('EMPTY');
        emptyCount++;
      } else {
        if ((passwordCounts.get(pass) || 0) > 1) {
          issues.push('REUSED');
          reusedCount++;
        }
        if (entropy < this.WEAK_ENTROPY_BITS) {
          issues.push('WEAK');
          weakCount++;
        }
      }

      if (issues.length > 0) {
        flaggedItemIds.add(item.id);
        findings.push({
          credential: item,
          issues,
          entropy,
        });
      }
    }

    // Sort findings: reused first, then empty, then weak; ties broken by lower entropy
    findings.sort((a, b) => {
      const getPriority = (f: CredentialFinding) => {
        if (f.issues.includes('REUSED')) return 0;
        if (f.issues.includes('EMPTY')) return 1;
        return 2;
      };
      const prioDiff = getPriority(a) - getPriority(b);
      if (prioDiff !== 0) return prioDiff;
      return a.entropy - b.entropy;
    });

    const total = checkableItems.length;
    const flaggedCount = flaggedItemIds.size;
    const rawScore = total > 0 ? Math.round(((total - flaggedCount) / total) * 100) : 100;
    const score = Math.max(0, Math.min(100, rawScore));

    return {
      score,
      reusedCount,
      weakCount,
      emptyCount,
      totalCount: total,
      findings,
    };
  }
}
