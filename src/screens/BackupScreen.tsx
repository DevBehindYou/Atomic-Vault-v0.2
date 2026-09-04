import React, { useState } from 'react';
import {
  ArrowLeft,
  Download,
  Upload,
  AlertCircle,
  Eye,
  EyeOff,
  CheckCircle2,
  FileSpreadsheet,
  Archive,
  Table,
  Sparkles,
  ShieldAlert,
  ChevronDown,
} from 'lucide-react';
import { LiquidGlassCard } from '../components/LiquidGlassCard';
import { VaultExport } from '../types';
import { VaultCrypto } from '../crypto/vaultCrypto';
import { TrustLedger } from '../trust/trustLedger';
import {
  exportVaultToCsv,
  parseCsvString,
  autoDetectCsvMapping,
  processCsvImport,
  CsvMappingConfig,
} from '../utils/csvHelper';

interface BackupScreenProps {
  onExportData: () => VaultExport;
  onImportData: (data: VaultExport) => Promise<void>;
  onBack: () => void;
}

export const BackupScreen: React.FC<BackupScreenProps> = ({
  onExportData,
  onImportData,
  onBack,
}) => {
  const [activeTab, setActiveTab] = useState<'ENCRYPTED' | 'CSV'>('ENCRYPTED');

  // Encrypted Backup (.atvb) Export State
  const [exportPassphrase, setExportPassphrase] = useState('');
  const [confirmExportPassphrase, setConfirmExportPassphrase] = useState('');
  const [showExportPassphrase, setShowExportPassphrase] = useState(false);
  const [exportBusy, setExportBusy] = useState(false);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  // Encrypted Backup (.atvb) Import State
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importPassphrase, setImportPassphrase] = useState('');
  const [showImportPassphrase, setShowImportPassphrase] = useState(false);
  const [importBusy, setImportBusy] = useState(false);
  const [importSuccess, setImportSuccess] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  // CSV Export State
  const [csvExportSuccess, setCsvExportSuccess] = useState(false);
  const [csvExportError, setCsvExportError] = useState<string | null>(null);

  // CSV Import State
  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [csvHeaders, setCsvHeaders] = useState<string[]>([]);
  const [csvRows, setCsvRows] = useState<string[][]>([]);
  const [csvMapping, setCsvMapping] = useState<CsvMappingConfig>({
    title: '',
    username: '',
    password: '',
    url: '',
    totpSecret: '',
    folder: '',
    tags: '',
    notes: '',
    type: '',
  });
  const [showPreviewRows, setShowPreviewRows] = useState(true);
  const [csvImportBusy, setCsvImportBusy] = useState(false);
  const [csvImportSuccess, setCsvImportSuccess] = useState<string | null>(null);
  const [csvImportError, setCsvImportError] = useState<string | null>(null);

  const handleExport = async (e: React.FormEvent) => {
    e.preventDefault();
    setExportError(null);
    setExportSuccess(false);

    if (exportPassphrase.length < 8) {
      setExportError('Backup passphrase must be at least 8 characters');
      return;
    }
    if (exportPassphrase !== confirmExportPassphrase) {
      setExportError('Passphrases do not match');
      return;
    }

    setExportBusy(true);
    try {
      const data = onExportData();
      const json = JSON.stringify(data);
      const backupBytes = await VaultCrypto.exportBackup(json, exportPassphrase);

      // Trigger browser download
      const blob = new Blob([backupBytes as unknown as BlobPart], { type: 'application/octet-stream' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      a.href = url;
      a.download = `atomicvault-backup-${timestamp}.atvb`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      await TrustLedger.record('BACKUP_EXPORTED', null, null, null, 'backup', 'success');
      setExportSuccess(true);
      setExportPassphrase('');
      setConfirmExportPassphrase('');
    } catch (err: any) {
      setExportError(err?.message || 'Failed to export backup');
    } finally {
      setExportBusy(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setImportFile(e.target.files[0]);
      setImportError(null);
      setImportSuccess(null);
    }
  };

  const handleImport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!importFile) {
      setImportError('Please select a backup file (.atvb)');
      return;
    }
    if (!importPassphrase) {
      setImportError('Passphrase is required to decrypt backup');
      return;
    }

    setImportBusy(true);
    setImportError(null);
    setImportSuccess(null);

    try {
      const arrayBuffer = await importFile.arrayBuffer();
      const backupBytes = new Uint8Array(arrayBuffer);
      const json = await VaultCrypto.importBackup(backupBytes, importPassphrase);
      const parsed: VaultExport = JSON.parse(json);

      if (!parsed.items || !Array.isArray(parsed.items)) {
        throw new Error('Invalid vault backup payload');
      }

      await onImportData(parsed);
      await TrustLedger.record('BACKUP_IMPORTED', null, null, null, 'backup', 'success');
      setImportSuccess(`Restored ${parsed.items.length} items and ${parsed.folders?.length || 0} folders successfully.`);
      setImportFile(null);
      setImportPassphrase('');
    } catch (err: any) {
      setImportError(err?.message || 'Incorrect passphrase or corrupted backup file');
    } finally {
      setImportBusy(false);
    }
  };

  // CSV Export Handler
  const handleCsvExport = () => {
    setCsvExportError(null);
    setCsvExportSuccess(false);
    try {
      const currentVault = onExportData();
      const csvString = exportVaultToCsv(currentVault.items, currentVault.folders || []);
      const blob = new Blob([csvString], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      a.href = url;
      a.download = `atomicvault-export-${timestamp}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      TrustLedger.record('BACKUP_EXPORTED', null, null, null, 'backup_csv', 'success');
      setCsvExportSuccess(true);
      setTimeout(() => setCsvExportSuccess(false), 5000);
    } catch (err: any) {
      setCsvExportError(err?.message || 'Failed to export CSV file');
    }
  };

  // CSV File Change Handler
  const handleCsvFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setCsvFile(file);
    setCsvImportError(null);
    setCsvImportSuccess(null);

    try {
      const text = await file.text();
      const rows = parseCsvString(text);
      if (rows.length === 0) {
        throw new Error('CSV file appears to be empty');
      }

      const headers = rows[0];
      const dataRows = rows.slice(1);

      if (dataRows.length === 0) {
        throw new Error('CSV file contains headers but no data records');
      }

      setCsvHeaders(headers);
      setCsvRows(dataRows);

      // Auto-detect mappings from header names
      const detected = autoDetectCsvMapping(headers);
      setCsvMapping(detected);
    } catch (err: any) {
      setCsvImportError(err?.message || 'Failed to parse CSV file');
      setCsvFile(null);
      setCsvHeaders([]);
      setCsvRows([]);
    }
  };

  // CSV Import Execution Handler
  const handleExecuteCsvImport = async () => {
    if (!csvFile || csvRows.length === 0) {
      setCsvImportError('Please select a valid CSV file first');
      return;
    }
    if (!csvMapping.title) {
      setCsvImportError('Please map at least the "Title / Service Name" column');
      return;
    }

    setCsvImportBusy(true);
    setCsvImportError(null);
    setCsvImportSuccess(null);

    try {
      const currentVault = onExportData();
      const { importedItems, createdFolders, createdTags } = processCsvImport(
        csvHeaders,
        csvRows,
        csvMapping,
        currentVault.folders || [],
        currentVault.tags || []
      );

      if (importedItems.length === 0) {
        throw new Error('No items could be imported. Verify that the mapped Title column is not empty.');
      }

      const importPayload: VaultExport = {
        version: 1,
        exportedAt: Date.now(),
        items: importedItems,
        folders: [...(currentVault.folders || []), ...createdFolders],
        tags: [...(currentVault.tags || []), ...createdTags],
        settings: currentVault.settings,
      };

      await onImportData(importPayload);
      await TrustLedger.record('BACKUP_IMPORTED', null, null, null, 'backup_csv', 'success');

      setCsvImportSuccess(
        `Imported ${importedItems.length} credential${importedItems.length === 1 ? '' : 's'}${
          createdFolders.length > 0 ? `, created ${createdFolders.length} folder${createdFolders.length === 1 ? '' : 's'}` : ''
        }${createdTags.length > 0 ? `, created ${createdTags.length} tag${createdTags.length === 1 ? '' : 's'}` : ''}.`
      );
      setCsvFile(null);
      setCsvHeaders([]);
      setCsvRows([]);
    } catch (err: any) {
      setCsvImportError(err?.message || 'Failed to import CSV records');
    } finally {
      setCsvImportBusy(false);
    }
  };

  const reDetectHeaders = () => {
    if (csvHeaders.length > 0) {
      const detected = autoDetectCsvMapping(csvHeaders);
      setCsvMapping(detected);
    }
  };

  const totalVaultItems = onExportData().items.length;

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
        <h2 className="font-bold text-base sm:text-lg text-white">Vault Backup & Data Transfer</h2>
      </header>

      <main className="max-w-2xl mx-auto px-4 sm:px-6 pt-6 space-y-6">
        {/* Format Selector Tabs */}
        <div className="flex p-1 bg-white/5 border border-white/10 rounded-2xl">
          <button
            type="button"
            onClick={() => setActiveTab('ENCRYPTED')}
            className={`flex-1 py-2 px-3 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer ${
              activeTab === 'ENCRYPTED'
                ? 'bg-white text-black shadow-lg'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            <Archive className="w-3.5 h-3.5" />
            <span>Encrypted Archive (.atvb)</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('CSV')}
            className={`flex-1 py-2 px-3 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer ${
              activeTab === 'CSV'
                ? 'bg-white text-black shadow-lg'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            <FileSpreadsheet className="w-3.5 h-3.5" />
            <span>CSV Spreadsheet (.csv)</span>
          </button>
        </div>

        {activeTab === 'ENCRYPTED' ? (
          <>
            <p className="text-xs text-neutral-400">
              AtomicVault archives are standalone AES-256-GCM encrypted binary files (format <code>.atvb</code>).
              They include your credentials, custom fields, tags, and folders encrypted with a PBKDF2 passphrase.
            </p>

            {/* Export Section */}
            <div className="space-y-2">
              <h3 className="text-xs font-semibold text-neutral-300 uppercase tracking-wider flex items-center gap-1.5">
                <Download className="w-3.5 h-3.5 text-white" />
                <span>Export Encrypted Backup</span>
              </h3>

              <LiquidGlassCard className="space-y-4">
                {exportSuccess && (
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs rounded-xl flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>Backup file exported and downloaded successfully!</span>
                  </div>
                )}

                {exportError && (
                  <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{exportError}</span>
                  </div>
                )}

                <form onSubmit={handleExport} className="space-y-3">
                  <div className="space-y-1">
                    <label className="text-xs text-neutral-300">Backup Passphrase</label>
                    <div className="relative">
                      <input
                        type={showExportPassphrase ? 'text' : 'password'}
                        value={exportPassphrase}
                        onChange={(e) => setExportPassphrase(e.target.value)}
                        placeholder="Min. 8 characters"
                        className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2 text-xs text-white placeholder-neutral-500 pr-10 outline-none"
                      />
                      <button
                        type="button"
                        onClick={() => setShowExportPassphrase(!showExportPassphrase)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-white"
                      >
                        {showExportPassphrase ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                      </button>
                    </div>
                  </div>

                  <div className="space-y-1">
                    <label className="text-xs text-neutral-300">Confirm Passphrase</label>
                    <input
                      type={showExportPassphrase ? 'text' : 'password'}
                      value={confirmExportPassphrase}
                      onChange={(e) => setConfirmExportPassphrase(e.target.value)}
                      placeholder="Repeat passphrase"
                      className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2 text-xs text-white placeholder-neutral-500 outline-none"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={exportBusy}
                    className="w-full py-2.5 px-4 bg-white text-black font-semibold text-xs rounded-xl hover:bg-neutral-200 transition-colors flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>{exportBusy ? 'Encrypting & Packaging...' : `Download Encrypted Backup (${totalVaultItems} Items)`}</span>
                  </button>
                </form>
              </LiquidGlassCard>
            </div>

            {/* Restore Section */}
            <div className="space-y-2">
              <h3 className="text-xs font-semibold text-neutral-300 uppercase tracking-wider flex items-center gap-1.5">
                <Upload className="w-3.5 h-3.5 text-white" />
                <span>Restore Encrypted Backup</span>
              </h3>

              <LiquidGlassCard className="space-y-4">
                {importSuccess && (
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs rounded-xl flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{importSuccess}</span>
                  </div>
                )}

                {importError && (
                  <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{importError}</span>
                  </div>
                )}

                <form onSubmit={handleImport} className="space-y-3">
                  <div className="space-y-1">
                    <label className="text-xs text-neutral-300">Select .atvb Backup File</label>
                    <input
                      type="file"
                      accept=".atvb,application/octet-stream"
                      onChange={handleFileChange}
                      className="w-full text-xs text-neutral-400 file:mr-3 file:py-1.5 file:px-3 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-white/10 file:text-white hover:file:bg-white/15 cursor-pointer"
                    />
                  </div>

                  <div className="space-y-1">
                    <label className="text-xs text-neutral-300">Backup Passphrase</label>
                    <div className="relative">
                      <input
                        type={showImportPassphrase ? 'text' : 'password'}
                        value={importPassphrase}
                        onChange={(e) => setImportPassphrase(e.target.value)}
                        placeholder="Passphrase used when exporting"
                        className="w-full bg-white/5 border border-white/15 focus:border-white/40 rounded-xl px-3.5 py-2 text-xs text-white placeholder-neutral-500 pr-10 outline-none"
                      />
                      <button
                        type="button"
                        onClick={() => setShowImportPassphrase(!showImportPassphrase)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-white"
                      >
                        {showImportPassphrase ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                      </button>
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={importBusy || !importFile}
                    className="w-full py-2.5 px-4 bg-white/10 hover:bg-white/15 border border-white/20 text-white font-semibold text-xs rounded-xl transition-colors flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    <span>{importBusy ? 'Decrypting & Restoring...' : 'Restore Vault from Backup'}</span>
                  </button>
                </form>
              </LiquidGlassCard>
            </div>
          </>
        ) : (
          /* CSV IMPORT & EXPORT SECTION */
          <>
            <div className="p-3 bg-amber-500/10 border border-amber-500/30 rounded-xl flex items-start gap-2.5 text-xs text-amber-300 leading-relaxed">
              <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5 text-amber-400" />
              <div>
                <strong className="text-amber-200">Plaintext Warning:</strong> CSV files are unencrypted plain text. Passwords, TOTP secrets, and notes in CSV files can be viewed by anyone with file access. Ensure you delete or encrypt the CSV file after importing or migrating.
              </div>
            </div>

            {/* CSV Export */}
            <div className="space-y-2">
              <h3 className="text-xs font-semibold text-neutral-300 uppercase tracking-wider flex items-center gap-1.5">
                <Download className="w-3.5 h-3.5 text-white" />
                <span>Export Vault to CSV</span>
              </h3>

              <LiquidGlassCard className="space-y-4">
                <p className="text-xs text-neutral-400">
                  Export all your vault items, folders, tags, and payment details into an RFC 4180 compliant CSV spreadsheet compatible with Bitwarden, 1Password, KeePass, and Excel.
                </p>

                {csvExportSuccess && (
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs rounded-xl flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>CSV file successfully exported and downloaded!</span>
                  </div>
                )}

                {csvExportError && (
                  <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{csvExportError}</span>
                  </div>
                )}

                <button
                  type="button"
                  onClick={handleCsvExport}
                  className="w-full py-2.5 px-4 bg-white text-black font-semibold text-xs rounded-xl hover:bg-neutral-200 transition-colors flex items-center justify-center gap-2 cursor-pointer"
                >
                  <FileSpreadsheet className="w-3.5 h-3.5" />
                  <span>Export {totalVaultItems} Items to CSV</span>
                </button>
              </LiquidGlassCard>
            </div>

            {/* CSV Import with Field Header Mapping */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-semibold text-neutral-300 uppercase tracking-wider flex items-center gap-1.5">
                  <Upload className="w-3.5 h-3.5 text-white" />
                  <span>Import from CSV (with Header Mapping)</span>
                </h3>
                {csvHeaders.length > 0 && (
                  <button
                    type="button"
                    onClick={reDetectHeaders}
                    className="text-[11px] text-neutral-400 hover:text-white flex items-center gap-1 cursor-pointer"
                  >
                    <Sparkles className="w-3 h-3 text-amber-400" />
                    <span>Auto-Detect Headers</span>
                  </button>
                )}
              </div>

              <LiquidGlassCard className="space-y-4">
                {csvImportSuccess && (
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs rounded-xl flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{csvImportSuccess}</span>
                  </div>
                )}

                {csvImportError && (
                  <div className="p-3 bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs rounded-xl flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{csvImportError}</span>
                  </div>
                )}

                <div className="space-y-1">
                  <label className="text-xs text-neutral-300">Select CSV File (.csv)</label>
                  <input
                    type="file"
                    accept=".csv,text/csv,text/plain"
                    onChange={handleCsvFileChange}
                    className="w-full text-xs text-neutral-400 file:mr-3 file:py-1.5 file:px-3 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-white/10 file:text-white hover:file:bg-white/15 cursor-pointer"
                  />
                  <p className="text-[11px] text-neutral-500 pt-0.5">
                    Supports exports from Chrome, Bitwarden, LastPass, 1Password, Dashlane, and KeePass.
                  </p>
                </div>

                {csvHeaders.length > 0 && (
                  <div className="space-y-4 pt-2 border-t border-white/10">
                    <div className="flex items-center justify-between">
                      <div>
                        <h4 className="text-xs font-semibold text-white">Map CSV Columns to Vault Fields</h4>
                        <p className="text-[11px] text-neutral-400">
                          Detected {csvHeaders.length} columns and {csvRows.length} rows. Select which column corresponds to each field.
                        </p>
                      </div>
                      <span className="text-[10px] font-mono bg-white/10 px-2 py-0.5 rounded text-neutral-300">
                        {csvRows.length} rows found
                      </span>
                    </div>

                    {/* Mapping Grid */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                      {/* Title (Required) */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 flex items-center gap-1 font-medium">
                          <span>Title / Account Name</span>
                          <span className="text-rose-400">*</span>
                        </label>
                        <select
                          value={csvMapping.title}
                          onChange={(e) => setCsvMapping({ ...csvMapping, title: e.target.value })}
                          className={`w-full bg-white/5 border rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer ${
                            csvMapping.title ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-rose-500/50'
                          }`}
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">-- Select Column --</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Username */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 font-medium">Username / Login / Email</label>
                        <select
                          value={csvMapping.username}
                          onChange={(e) => setCsvMapping({ ...csvMapping, username: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Skip / Not Mapped)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Password */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 font-medium">Password</label>
                        <select
                          value={csvMapping.password}
                          onChange={(e) => setCsvMapping({ ...csvMapping, password: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Skip / Not Mapped)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Website / URL */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 font-medium">Website / URL</label>
                        <select
                          value={csvMapping.url}
                          onChange={(e) => setCsvMapping({ ...csvMapping, url: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Skip / Not Mapped)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* TOTP Secret */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 font-medium">TOTP / 2FA Secret</label>
                        <select
                          value={csvMapping.totpSecret}
                          onChange={(e) => setCsvMapping({ ...csvMapping, totpSecret: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Skip / Not Mapped)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Folder / Category */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 font-medium">Folder / Category</label>
                        <select
                          value={csvMapping.folder}
                          onChange={(e) => setCsvMapping({ ...csvMapping, folder: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Skip / Not Mapped)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Tags */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 font-medium">Tags (e.g. Work; Finance)</label>
                        <select
                          value={csvMapping.tags}
                          onChange={(e) => setCsvMapping({ ...csvMapping, tags: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Skip / Not Mapped)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Item Type */}
                      <div className="space-y-1">
                        <label className="text-neutral-300 font-medium">Item Type (Login, Card, Note)</label>
                        <select
                          value={csvMapping.type}
                          onChange={(e) => setCsvMapping({ ...csvMapping, type: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Default: Login Credential)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* Notes */}
                      <div className="sm:col-span-2 space-y-1">
                        <label className="text-neutral-300 font-medium">Notes / Comments</label>
                        <select
                          value={csvMapping.notes}
                          onChange={(e) => setCsvMapping({ ...csvMapping, notes: e.target.value })}
                          className="w-full bg-white/5 border border-white/15 rounded-xl px-2.5 py-1.5 text-xs text-white outline-none cursor-pointer"
                        >
                          <option value="" className="bg-neutral-900 text-neutral-400">(Skip / Not Mapped)</option>
                          {csvHeaders.map((h) => (
                            <option key={h} value={h} className="bg-neutral-900 text-white">
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>

                    {/* Data Preview Table */}
                    <div className="space-y-2 pt-2">
                      <button
                        type="button"
                        onClick={() => setShowPreviewRows(!showPreviewRows)}
                        className="text-xs text-neutral-400 hover:text-white flex items-center gap-1.5 cursor-pointer"
                      >
                        <Table className="w-3.5 h-3.5" />
                        <span>{showPreviewRows ? 'Hide Sample Preview' : 'Show Sample Preview (First 2 Rows)'}</span>
                        <ChevronDown className={`w-3.5 h-3.5 transition-transform ${showPreviewRows ? 'rotate-180' : ''}`} />
                      </button>

                      {showPreviewRows && csvRows.length > 0 && (
                        <div className="overflow-x-auto rounded-xl border border-white/10 bg-black/40 text-[11px]">
                          <table className="w-full text-left border-collapse">
                            <thead>
                              <tr className="border-b border-white/10 bg-white/5 text-neutral-300">
                                <th className="p-2 font-medium">Row</th>
                                <th className="p-2 font-medium">Title ({csvMapping.title || 'unmapped'})</th>
                                <th className="p-2 font-medium">Username ({csvMapping.username || 'unmapped'})</th>
                                <th className="p-2 font-medium">URL ({csvMapping.url || 'unmapped'})</th>
                                <th className="p-2 font-medium">Folder ({csvMapping.folder || 'unmapped'})</th>
                              </tr>
                            </thead>
                            <tbody>
                              {csvRows.slice(0, 2).map((r, idx) => {
                                const titleIdx = csvHeaders.indexOf(csvMapping.title);
                                const userIdx = csvHeaders.indexOf(csvMapping.username);
                                const urlIdx = csvHeaders.indexOf(csvMapping.url);
                                const folderIdx = csvHeaders.indexOf(csvMapping.folder);

                                return (
                                  <tr key={idx} className="border-b border-white/5 last:border-b-0 text-neutral-300">
                                    <td className="p-2 font-mono text-neutral-500">{idx + 1}</td>
                                    <td className="p-2 font-medium text-white">{titleIdx >= 0 ? r[titleIdx] || '—' : '—'}</td>
                                    <td className="p-2 font-mono text-neutral-400">{userIdx >= 0 ? r[userIdx] || '—' : '—'}</td>
                                    <td className="p-2 truncate max-w-[120px] text-neutral-400">{urlIdx >= 0 ? r[urlIdx] || '—' : '—'}</td>
                                    <td className="p-2 text-neutral-400">{folderIdx >= 0 ? r[folderIdx] || '—' : '—'}</td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>

                    <button
                      type="button"
                      onClick={handleExecuteCsvImport}
                      disabled={csvImportBusy || !csvMapping.title}
                      className="w-full py-2.5 px-4 bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-xs rounded-xl transition-colors flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
                    >
                      <Upload className="w-3.5 h-3.5" />
                      <span>{csvImportBusy ? 'Importing & Encrypting...' : `Import ${csvRows.length} Credentials into Vault`}</span>
                    </button>
                  </div>
                )}
              </LiquidGlassCard>
            </div>
          </>
        )}
      </main>
    </div>
  );
};
