import { CredentialPlain, FolderPlain, TagPlain, VaultItemType } from '../types';

/**
 * Escapes a single value for CSV output in accordance with RFC 4180.
 */
export function escapeCsvField(val: string | number | null | undefined): string {
  if (val === null || val === undefined) return '';
  const str = String(val);
  if (str.includes(',') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

/**
 * Parses raw CSV string into an array of string arrays (rows and cells).
 * Correctly handles multiline text within quotes, escaped quotes (""), and commas.
 */
export function parseCsvString(csvText: string): string[][] {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentCell = '';
  let insideQuotes = false;

  for (let i = 0; i < csvText.length; i++) {
    const char = csvText[i];
    const nextChar = csvText[i + 1];

    if (insideQuotes) {
      if (char === '"') {
        if (nextChar === '"') {
          currentCell += '"';
          i++; // skip escaped quote
        } else {
          insideQuotes = false;
        }
      } else {
        currentCell += char;
      }
    } else {
      if (char === '"') {
        insideQuotes = true;
      } else if (char === ',') {
        currentRow.push(currentCell.trim());
        currentCell = '';
      } else if (char === '\r') {
        if (nextChar === '\n') {
          i++; // skip LF after CR
        }
        currentRow.push(currentCell.trim());
        if (currentRow.some((c) => c !== '')) {
          rows.push(currentRow);
        }
        currentRow = [];
        currentCell = '';
      } else if (char === '\n') {
        currentRow.push(currentCell.trim());
        if (currentRow.some((c) => c !== '')) {
          rows.push(currentRow);
        }
        currentRow = [];
        currentCell = '';
      } else {
        currentCell += char;
      }
    }
  }

  if (currentCell || currentRow.length > 0) {
    currentRow.push(currentCell.trim());
    if (currentRow.some((c) => c !== '')) {
      rows.push(currentRow);
    }
  }

  return rows;
}

/**
 * Exports vault items into a standardized, RFC-compliant CSV string.
 */
export function exportVaultToCsv(items: CredentialPlain[], folders: FolderPlain[]): string {
  const folderMap = new Map<string, string>();
  folders.forEach((f) => folderMap.set(f.id, f.name));

  const headers = [
    'Title',
    'Type',
    'Username',
    'Password',
    'URL',
    'TOTP_Secret',
    'Folder',
    'Tags',
    'Notes',
    'Cardholder',
    'Card_Number',
    'Card_Expiry',
    'Card_CVV',
    'Card_PIN',
  ];

  const lines: string[] = [headers.join(',')];

  for (const item of items) {
    const folderName = item.folderId ? folderMap.get(item.folderId) || '' : '';
    const tagNames = (item.tags || []).map((t) => t.name).join('; ');

    // Extract custom fields for payment cards
    const customFields = item.customFields || [];
    const getVal = (key: string) =>
      customFields.find((f) => f.label.toLowerCase().includes(key.toLowerCase()))?.value || '';

    const row = [
      escapeCsvField(item.title),
      escapeCsvField(item.itemType || 'LOGIN'),
      escapeCsvField(item.username),
      escapeCsvField(item.password),
      escapeCsvField(item.uriMatchPattern),
      escapeCsvField(item.totpSecret),
      escapeCsvField(folderName),
      escapeCsvField(tagNames),
      escapeCsvField(item.notes),
      escapeCsvField(getVal('cardholder')),
      escapeCsvField(getVal('card number')),
      escapeCsvField(getVal('expiration') || getVal('expiry')),
      escapeCsvField(getVal('cvv') || getVal('cvc')),
      escapeCsvField(getVal('pin')),
    ];

    lines.push(row.join(','));
  }

  return lines.join('\r\n');
}

export interface CsvMappingConfig {
  title: string;
  username: string;
  password: string;
  url: string;
  totpSecret: string;
  folder: string;
  tags: string;
  notes: string;
  type: string;
}

/**
 * Automatically inspects CSV headers and generates best-guess mapping.
 */
export function autoDetectCsvMapping(headers: string[]): CsvMappingConfig {
  const cleanHeaders = headers.map((h) => h.toLowerCase().replace(/[^a-z0-9]/g, ''));

  const findMatch = (candidates: string[]): string => {
    for (let i = 0; i < cleanHeaders.length; i++) {
      const header = cleanHeaders[i];
      for (const candidate of candidates) {
        if (header === candidate || header.includes(candidate)) {
          return headers[i];
        }
      }
    }
    return '';
  };

  return {
    title: findMatch(['title', 'name', 'account', 'loginname', 'site', 'service']),
    username: findMatch(['username', 'user', 'email', 'login', 'accountname']),
    password: findMatch(['password', 'pass', 'code', 'secret']),
    url: findMatch(['url', 'uri', 'website', 'domain', 'link']),
    totpSecret: findMatch(['totp', 'otp', 'twofactor', '2fa', 'authenticator']),
    folder: findMatch(['folder', 'category', 'group', 'collection']),
    tags: findMatch(['tag', 'tags', 'label', 'labels']),
    notes: findMatch(['note', 'notes', 'comment', 'comments', 'extra', 'memo']),
    type: findMatch(['type', 'itemtype', 'kind']),
  };
}

/**
 * Transforms parsed CSV rows and user header mapping into vault export format.
 */
export function processCsvImport(
  headers: string[],
  rows: string[][],
  mapping: CsvMappingConfig,
  existingFolders: FolderPlain[],
  existingTags: TagPlain[]
): {
  importedItems: CredentialPlain[];
  createdFolders: FolderPlain[];
  createdTags: TagPlain[];
} {
  const headerIndex = new Map<string, number>();
  headers.forEach((h, i) => headerIndex.set(h, i));

  const getCell = (row: string[], mappedHeader: string): string => {
    if (!mappedHeader) return '';
    const idx = headerIndex.get(mappedHeader);
    if (idx === undefined || idx >= row.length) return '';
    return row[idx] || '';
  };

  const folderMap = new Map<string, string>(); // name lower -> id
  existingFolders.forEach((f) => folderMap.set(f.name.toLowerCase(), f.id));

  const tagMap = new Map<string, TagPlain>(); // name lower -> TagPlain
  existingTags.forEach((t) => tagMap.set(t.name.toLowerCase(), t));

  const newFolders: FolderPlain[] = [];
  const newTags: TagPlain[] = [];
  const importedItems: CredentialPlain[] = [];

  const defaultColors = ['#10B981', '#6366F1', '#F59E0B', '#EC4899', '#06B6D4', '#8B5CF6'];

  for (const row of rows) {
    const rawTitle = getCell(row, mapping.title);
    if (!rawTitle.trim()) {
      continue; // Skip rows without title
    }

    const title = rawTitle.trim();
    const username = getCell(row, mapping.username).trim();
    const password = getCell(row, mapping.password);
    const url = getCell(row, mapping.url).trim();
    const totp = getCell(row, mapping.totpSecret).trim();
    const folderRaw = getCell(row, mapping.folder).trim();
    const tagsRaw = getCell(row, mapping.tags).trim();
    const notes = getCell(row, mapping.notes).trim();
    const rawType = getCell(row, mapping.type).trim().toUpperCase();

    let itemType: VaultItemType = 'LOGIN';
    if (rawType.includes('CARD') || rawType.includes('PAYMENT')) itemType = 'PAYMENT_CARD';
    else if (rawType.includes('NOTE')) itemType = 'SECURE_NOTE';
    else if (rawType.includes('ID') || rawType.includes('IDENTITY')) itemType = 'IDENTITY';

    // Resolve or create folder
    let folderId: string | null = null;
    if (folderRaw) {
      const lowerFolder = folderRaw.toLowerCase();
      if (folderMap.has(lowerFolder)) {
        folderId = folderMap.get(lowerFolder)!;
      } else {
        const createdFolder: FolderPlain = {
          id: `f_${crypto.randomUUID().slice(0, 8)}`,
          name: folderRaw,
        };
        folderMap.set(lowerFolder, createdFolder.id);
        newFolders.push(createdFolder);
        folderId = createdFolder.id;
      }
    }

    // Resolve or create tags
    const itemTags: TagPlain[] = [];
    if (tagsRaw) {
      const splitTags = tagsRaw.split(/[;,]/).map((t) => t.trim()).filter(Boolean);
      for (const tName of splitTags) {
        const lowerTag = tName.toLowerCase();
        if (tagMap.has(lowerTag)) {
          itemTags.push(tagMap.get(lowerTag)!);
        } else {
          const color = defaultColors[newTags.length % defaultColors.length];
          const createdTag: TagPlain = {
            id: `t_${crypto.randomUUID().slice(0, 8)}`,
            name: tName,
            color,
          };
          tagMap.set(lowerTag, createdTag);
          newTags.push(createdTag);
          itemTags.push(createdTag);
        }
      }
    }

    const item: CredentialPlain = {
      id: crypto.randomUUID(),
      title,
      username,
      password,
      notes,
      uriMatchPattern: url || null,
      androidPackageName: null,
      totpSecret: totp,
      folderId,
      tags: itemTags,
      customFields: [],
      updatedAt: Date.now(),
      itemType,
    };

    importedItems.push(item);
  }

  return {
    importedItems,
    createdFolders: newFolders,
    createdTags: newTags,
  };
}
