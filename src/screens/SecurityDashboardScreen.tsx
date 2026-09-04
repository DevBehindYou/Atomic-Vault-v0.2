import React, { useState } from 'react';
import {
  ArrowLeft,
  ShieldCheck,
  Key,
  ChevronRight,
  FileText,
  Download,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';
import { VaultSecurityReport } from '../types';
import { LiquidGlassCard } from '../components/LiquidGlassCard';
import { exportTrustLedgerPdf } from '../utils/pdfExport';
import { TrustLedger } from '../trust/trustLedger';

interface SecurityDashboardScreenProps {
  report: VaultSecurityReport;
  onSelectFinding: (credentialId: string) => void;
  onBack: () => void;
}

export const SecurityDashboardScreen: React.FC<SecurityDashboardScreenProps> = ({
  report,
  onSelectFinding,
  onBack,
}) => {
  const [exportingPdf, setExportingPdf] = useState(false);
  const [pdfSuccess, setPdfSuccess] = useState<string | null>(null);
  const [pdfError, setPdfError] = useState<string | null>(null);

  const getScoreColor = (score: number) => {
    if (score >= 80) return 'text-emerald-400 border-emerald-500/30 bg-emerald-500/10';
    if (score >= 50) return 'text-amber-400 border-amber-500/30 bg-amber-500/10';
    return 'text-rose-400 border-rose-500/30 bg-rose-500/10';
  };

  const handleExportPdf = async () => {
    setExportingPdf(true);
    setPdfError(null);
    setPdfSuccess(null);
    try {
      const result = await exportTrustLedgerPdf(report);
      if (result.success) {
        setPdfSuccess(`Audit PDF exported with ${result.entryCount} ledger records.`);
        await TrustLedger.record('INTEGRITY_CHECK_COMPLETED', null, null, null, 'pdf_export', 'success');
        setTimeout(() => setPdfSuccess(null), 5000);
      } else {
        setPdfError(result.error || 'Failed to generate PDF document');
      }
    } catch (err: any) {
      setPdfError(err?.message || 'Error generating PDF');
    } finally {
      setExportingPdf(false);
    }
  };

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
          <h2 className="font-bold text-base sm:text-lg text-white">Security & Password Health</h2>
        </div>

        <button
          type="button"
          onClick={handleExportPdf}
          disabled={exportingPdf}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/15 border border-white/15 text-white text-xs font-semibold cursor-pointer transition-colors disabled:opacity-50"
          title="Export Trust Ledger Audit PDF"
        >
          <FileText className="w-3.5 h-3.5 text-emerald-400" />
          <span className="hidden sm:inline">{exportingPdf ? 'Generating...' : 'Export Audit PDF'}</span>
          <span className="sm:hidden">PDF</span>
        </button>
      </header>

      <main className="max-w-2xl mx-auto px-4 sm:px-6 pt-6 space-y-6">
        {pdfSuccess && (
          <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs rounded-xl flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{pdfSuccess}</span>
          </div>
        )}

        {pdfError && (
          <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{pdfError}</span>
          </div>
        )}

        {/* Score Banner */}
        <LiquidGlassCard className="text-center py-6 space-y-3">
          <div
            className={`inline-flex items-center justify-center w-20 h-20 rounded-full border-2 font-mono text-3xl font-bold ${getScoreColor(
              report.score
            )}`}
          >
            {report.score}%
          </div>
          <div>
            <h3 className="font-bold text-lg text-white">
              {report.score === 100
                ? 'Excellent Vault Security'
                : report.score >= 75
                ? 'Good Vault Security'
                : 'Attention Required'}
            </h3>
            <p className="text-xs text-neutral-400 max-w-sm mx-auto mt-1">
              {report.findings.length === 0
                ? 'No weak, empty, or reused passwords found across your login credentials.'
                : `${report.findings.length} credential(s) have compromised or weak entropy.`}
            </p>
          </div>

          {/* Metric cards */}
          <div className="grid grid-cols-3 gap-2.5 pt-3">
            <div className="p-3 rounded-xl bg-white/5 border border-white/10 space-y-0.5">
              <div className="text-[11px] text-neutral-400">Reused</div>
              <div className="font-mono text-lg font-bold text-rose-400">{report.reusedCount}</div>
            </div>
            <div className="p-3 rounded-xl bg-white/5 border border-white/10 space-y-0.5">
              <div className="text-[11px] text-neutral-400">Weak (&lt;50b)</div>
              <div className="font-mono text-lg font-bold text-amber-400">{report.weakCount}</div>
            </div>
            <div className="p-3 rounded-xl bg-white/5 border border-white/10 space-y-0.5">
              <div className="text-[11px] text-neutral-400">Empty</div>
              <div className="font-mono text-lg font-bold text-neutral-300">{report.emptyCount}</div>
            </div>
          </div>
        </LiquidGlassCard>

        {/* Findings List */}
        <div className="space-y-3">
          <h3 className="font-semibold text-sm text-neutral-300">Flagged Credentials</h3>

          {report.findings.length === 0 ? (
            <LiquidGlassCard className="text-center py-8 space-y-2">
              <ShieldCheck className="w-8 h-8 text-emerald-400 mx-auto" />
              <div className="text-sm font-semibold text-white">Zero Vulnerabilities Detected</div>
              <p className="text-xs text-neutral-400">
                All login credentials have unique passwords with sufficient cryptographic entropy.
              </p>
            </LiquidGlassCard>
          ) : (
            <div className="space-y-2">
              {report.findings.map((f) => (
                <LiquidGlassCard
                  key={f.credential.id}
                  onClick={() => onSelectFinding(f.credential.id)}
                  className="flex items-center justify-between p-3.5 hover:border-white/30 cursor-pointer"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-9 h-9 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center shrink-0">
                      <Key className="w-4 h-4 text-white" />
                    </div>
                    <div className="min-w-0 space-y-0.5">
                      <div className="font-semibold text-sm text-white truncate">
                        {f.credential.title}
                      </div>
                      <div className="text-xs text-neutral-400 truncate">
                        {f.credential.username || 'No username'}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <div className="flex items-center gap-1.5">
                      {f.issues.map((issue) => (
                        <span
                          key={issue}
                          className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
                            issue === 'REUSED'
                              ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                              : issue === 'WEAK'
                              ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                              : 'bg-neutral-500/20 text-neutral-300 border border-neutral-500/30'
                          }`}
                        >
                          {issue}
                        </span>
                      ))}
                    </div>
                    <ChevronRight className="w-4 h-4 text-neutral-500" />
                  </div>
                </LiquidGlassCard>
              ))}
            </div>
          )}
        </div>

        {/* Trust Ledger Audit Export Section */}
        <div className="space-y-3 pt-2">
          <h3 className="font-semibold text-sm text-neutral-300 flex items-center gap-2">
            <FileText className="w-4 h-4 text-emerald-400" />
            <span>Cryptographic Audit & Ledger Export</span>
          </h3>

          <LiquidGlassCard className="space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="space-y-1">
                <div className="text-xs font-semibold text-white flex items-center gap-2">
                  <span>Trust Ledger Audit Document</span>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                    Read-Only PDF
                  </span>
                </div>
                <p className="text-xs text-neutral-400 leading-relaxed">
                  Export complete event non-repudiation history, cryptographic HMAC digests, and chain integrity verification as a standardized PDF document for auditing and compliance.
                </p>
              </div>

              <button
                type="button"
                onClick={handleExportPdf}
                disabled={exportingPdf}
                className="py-2.5 px-4 rounded-xl bg-white text-black font-semibold text-xs hover:bg-neutral-200 transition-colors flex items-center justify-center gap-2 shrink-0 cursor-pointer disabled:opacity-50"
              >
                <Download className="w-3.5 h-3.5" />
                <span>{exportingPdf ? 'Generating PDF...' : 'Download Audit PDF'}</span>
              </button>
            </div>
          </LiquidGlassCard>
        </div>
      </main>
    </div>
  );
};
