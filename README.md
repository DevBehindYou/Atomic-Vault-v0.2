# AtomicVault (Web Edition)

An offline, zero-knowledge encrypted credential and password manager built with React, TypeScript, Vite, and Tailwind CSS.

Zero network transmission, zero cloud sync, zero telemetry — all cryptographic secrets and credential databases stay strictly on-device.

## Core Features & Architecture

- **Envelope Encryption (WebCrypto API)**:
  - Key Encryption Key (KEK) derived from master password using PBKDF2 (100,000 iterations, SHA-256) with high-entropy salt.
  - 256-bit Data Encryption Key (DEK) wrapped using AES-256-GCM.
  - All credentials, payment cards, identity records, and custom fields encrypted with AES-256-GCM.
- **Tamper-Evident Trust Ledger**:
  - Hash-chained audit trail of all security events (vault creations, unlocks, credential alterations, backups, integrity checks).
  - Every event is signed via HMAC-SHA256 rooted in a genesis state and verified through full chain re-computation.
- **Item Categories**:
  - **Login Credentials**: Username, password, TOTP 2FA secret (with live RFC 6238 code generation & countdown), URL match pattern, notes, and custom key-value pairs.
  - **Payment Cards**: Cardholder, formatted card number with brand detection (Visa, Mastercard, Amex, Discover), expiration date, CVV, ATM PIN, and liquid glass card preview.
  - **Identity Records**: Full legal name, passport/ID/SSN, phone, address, and notes.
  - **Secure Notes**: Encrypted freeform notes for sensitive documents and recovery instructions.
- **Security Audit & Password Health**:
  - Real-time Shannon entropy calculation for passwords.
  - Vault-wide audit detecting reused passwords, weak passwords (< 50 bits of entropy), and empty credentials.
- **Unbiased Password Generator**:
  - CSPRNG random generation with rejection sampling to eliminate modulo bias.
  - Custom length, character classes (uppercase, lowercase, digits, symbols), and ambiguous character filters.
- **Encrypted Standalone Backup (.atvb)**:
  - Export and import passphrase-protected binary `.atvb` files matching the specification with `ATVB` magic header.
- **Zero-Knowledge Privacy Proofs**:
  - Verified local audit confirming zero outbound network requests and cryptographic integrity.

## Development

```bash
npm install
npm run dev
```

Build for production:

```bash
npm run build
```
