import jsPDF from 'jspdf';
import { TrustLedger } from '../trust/trustLedger';
import { VaultSecurityReport } from '../types';

/**
 * Formats a timestamp into clean UTC string: YYYY-MM-DD HH:mm:ss UTC
 */
function formatUtcTimestamp(timestamp: number): string {
  const d = new Date(timestamp);
  const pad = (n: number) => n.toString().padStart(2, '0');
  const year = d.getUTCFullYear();
  const month = pad(d.getUTCMonth() + 1);
  const day = pad(d.getUTCDate());
  const hours = pad(d.getUTCHours());
  const minutes = pad(d.getUTCMinutes());
  const seconds = pad(d.getUTCSeconds());
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds} UTC`;
}

/**
 * Truncates a hash for clean tabular presentation (first 8 + ... + last 8)
 */
function truncateHash(hash: string | null): string {
  if (!hash) return '—';
  if (hash.length <= 18) return hash;
  return `${hash.slice(0, 8)}...${hash.slice(-8)}`;
}

/**
 * Generates and downloads a read-only PDF audit document containing the trust ledger history.
 */
export async function exportTrustLedgerPdf(
  report?: VaultSecurityReport
): Promise<{ success: boolean; entryCount: number; fileName: string; error?: string }> {
  try {
    const entries = TrustLedger.listEntries(1000);
    const brokenChainId = await TrustLedger.verifyChainIntegrity();
    const isChainValid = brokenChainId === null;
    const exportTime = Date.now();
    const formattedExportTime = formatUtcTimestamp(exportTime);

    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'a4',
    });

    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();
    const margin = 14;
    const contentWidth = pageWidth - margin * 2;

    let currentPage = 1;

    // Helper: draw page header
    const drawPageHeader = (pageNumber: number) => {
      // Header top bar
      doc.setFillColor(15, 15, 18);
      doc.rect(margin, 12, contentWidth, 20, 'F');

      // Top title
      doc.setTextColor(255, 255, 255);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(13);
      doc.text('ATOMICVAULT CRYPTOGRAPHIC AUDIT REPORT', margin + 5, 20);

      // Subtitle
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8.5);
      doc.setTextColor(180, 180, 190);
      doc.text(
        `Read-Only Tamper-Evident Trust Ledger Audit Log  •  Generated: ${formattedExportTime}`,
        margin + 5,
        27
      );

      // Page stamp on right
      doc.setFontSize(8);
      doc.setTextColor(220, 220, 230);
      doc.text(`Page ${pageNumber}`, pageWidth - margin - 15, 20);
    };

    // Helper: draw page footer
    const drawPageFooter = (pageNumber: number) => {
      doc.setDrawColor(220, 220, 230);
      doc.setLineWidth(0.3);
      doc.line(margin, pageHeight - 12, pageWidth - margin, pageHeight - 12);

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(7.5);
      doc.setTextColor(120, 120, 130);
      doc.text(
        'CONFIDENTIAL & CRYPTOGRAPHICALLY TAMPER-EVIDENT  •  HMAC-SHA-256 NON-REPUDIATION LEDGER',
        margin,
        pageHeight - 8
      );
      doc.text(
        `Page ${pageNumber}`,
        pageWidth - margin - 12,
        pageHeight - 8
      );
    };

    // --- Page 1 Header ---
    drawPageHeader(1);

    let y = 38;

    // --- Audit Summary / Verification Box ---
    doc.setFillColor(248, 249, 252);
    doc.setDrawColor(215, 220, 230);
    doc.roundedRect(margin, y, contentWidth, 34, 2, 2, 'FD');

    // Integrity badge
    if (isChainValid) {
      doc.setFillColor(220, 252, 231);
      doc.setDrawColor(134, 239, 172);
      doc.roundedRect(margin + 4, y + 4, 75, 7, 1.5, 1.5, 'FD');
      doc.setTextColor(22, 101, 52);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(7.5);
      doc.text('CRYPTOGRAPHIC CHAIN: VERIFIED TAMPER-PROOF', margin + 6, y + 8.8);
    } else {
      doc.setFillColor(254, 226, 226);
      doc.setDrawColor(248, 113, 113);
      doc.roundedRect(margin + 4, y + 4, 80, 7, 1.5, 1.5, 'FD');
      doc.setTextColor(153, 27, 27);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(7.5);
      doc.text('INTEGRITY VIOLATION DETECTED: INVALID HASH', margin + 6, y + 8.8);
    }

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(70, 70, 80);
    doc.text(`Total Audit Events: ${entries.length}`, margin + 85, y + 8.8);

    // Metadata lines inside box
    doc.setFontSize(7.8);
    doc.setTextColor(60, 64, 75);
    doc.text(`Ledger Algorithm: HMAC-SHA256 Blockchain with Genesis Anchor (GENESIS)`, margin + 5, y + 16);
    doc.text(`Storage Scope: Local Sandboxed Zero-Knowledge Client (Offline Verified)`, margin + 5, y + 21);

    if (report) {
      doc.text(
        `Vault Health Score: ${report.score}%  |  Reused: ${report.reusedCount}  |  Weak: ${report.weakCount}  |  Empty: ${report.emptyCount}  |  Total Items: ${report.totalCount}`,
        margin + 5,
        y + 26
      );
    } else {
      doc.text(`Vault Security Score: Clean Audit Snapshot`, margin + 5, y + 26);
    }

    doc.text(`Status: Read-Only Exported Document for Compliance & Privacy Verification`, margin + 5, y + 31);

    y += 40;

    // --- Table Header ---
    const drawTableHeader = (posY: number) => {
      doc.setFillColor(30, 32, 40);
      doc.rect(margin, posY, contentWidth, 7, 'F');
      doc.setTextColor(255, 255, 255);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(7.5);

      doc.text('Timestamp (UTC)', margin + 2, posY + 4.8);
      doc.text('Event Type', margin + 40, posY + 4.8);
      doc.text('Source / Auth', margin + 84, posY + 4.8);
      doc.text('Result', margin + 118, posY + 4.8);
      doc.text('HMAC Hash (Digest)', margin + 140, posY + 4.8);
    };

    drawTableHeader(y);
    y += 7;

    // --- Table Body ---
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7);

    const rowHeight = 6.2;

    if (entries.length === 0) {
      doc.setTextColor(120, 120, 130);
      doc.text('No ledger events recorded yet.', margin + 4, y + 6);
      y += 10;
    } else {
      for (let i = 0; i < entries.length; i++) {
        const entry = entries[i];

        // Check page boundary
        if (y + rowHeight > pageHeight - 18) {
          drawPageFooter(currentPage);
          doc.addPage();
          currentPage++;
          drawPageHeader(currentPage);
          y = 38;
          drawTableHeader(y);
          y += 7;
        }

        // Alternating row background
        if (i % 2 === 1) {
          doc.setFillColor(248, 249, 251);
          doc.rect(margin, y, contentWidth, rowHeight, 'F');
        }

        // Bottom hairline
        doc.setDrawColor(235, 238, 245);
        doc.setLineWidth(0.15);
        doc.line(margin, y + rowHeight, margin + contentWidth, y + rowHeight);

        // Date
        doc.setTextColor(50, 50, 60);
        doc.text(formatUtcTimestamp(entry.timestamp), margin + 2, y + 4.2);

        // Event Type
        doc.setFont('helvetica', 'bold');
        if (entry.result === 'failure') {
          doc.setTextColor(190, 18, 60);
        } else if (entry.eventType.includes('CREATED') || entry.eventType.includes('UNLOCKED')) {
          doc.setTextColor(16, 110, 60);
        } else {
          doc.setTextColor(30, 40, 60);
        }
        doc.text(entry.eventType, margin + 40, y + 4.2);

        // Source & Auth
        doc.setFont('helvetica', 'normal');
        doc.setTextColor(90, 95, 105);
        const sourceAuth = `${entry.source || 'app'}${entry.authenticationType ? ` / ${entry.authenticationType}` : ''}`;
        doc.text(sourceAuth.slice(0, 20), margin + 84, y + 4.2);

        // Result
        if (entry.result === 'success') {
          doc.setTextColor(22, 101, 52);
          doc.text('SUCCESS', margin + 118, y + 4.2);
        } else {
          doc.setTextColor(185, 28, 28);
          doc.text('FAILURE', margin + 118, y + 4.2);
        }

        // HMAC Hash
        doc.setFont('courier', 'normal');
        doc.setFontSize(6.5);
        doc.setTextColor(100, 100, 115);
        doc.text(truncateHash(entry.eventHash), margin + 140, y + 4.2);

        doc.setFont('helvetica', 'normal');
        doc.setFontSize(7);
        y += rowHeight;
      }
    }

    // Draw final page footer
    drawPageFooter(currentPage);

    // Save PDF
    const timestampStr = new Date().toISOString().replace(/[:.]/g, '-');
    const fileName = `atomicvault-audit-ledger-${timestampStr}.pdf`;
    doc.save(fileName);

    return {
      success: true,
      entryCount: entries.length,
      fileName,
    };
  } catch (err: any) {
    console.error('Failed to export Trust Ledger PDF:', err);
    return {
      success: false,
      entryCount: 0,
      fileName: '',
      error: err?.message || 'Failed to generate PDF document',
    };
  }
}
