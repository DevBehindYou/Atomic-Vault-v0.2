---
name: Liquid Glass Cryptographic Vault
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1b1b1b'
  surface-container: '#1f1f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353535'
  on-surface: '#e2e2e2'
  on-surface-variant: '#c4c7c8'
  inverse-surface: '#e2e2e2'
  inverse-on-surface: '#303030'
  outline: '#8e9192'
  outline-variant: '#444748'
  surface-tint: '#c6c6c7'
  primary: '#ffffff'
  on-primary: '#2f3131'
  primary-container: '#e2e2e2'
  on-primary-container: '#636565'
  inverse-primary: '#5d5f5f'
  secondary: '#4edea3'
  on-secondary: '#003824'
  secondary-container: '#00a572'
  on-secondary-container: '#00311f'
  tertiary: '#ffffff'
  on-tertiary: '#67001b'
  tertiary-container: '#ffdadb'
  on-tertiary-container: '#c51640'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c7'
  on-primary-fixed: '#1a1c1c'
  on-primary-fixed-variant: '#454747'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#ffdadb'
  tertiary-fixed-dim: '#ffb2b7'
  on-tertiary-fixed: '#40000d'
  on-tertiary-fixed-variant: '#92002a'
  background: '#131313'
  on-background: '#e2e2e2'
  surface-variant: '#353535'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.02em
  caption:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  micro:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
  space-xxl: 2rem
---

## Brand & Style
This design system defines an ultra-secure, zero-knowledge cryptographic interface where tactile optics meet clinical digital sovereignty. By fusing extreme optical minimalism with multi-layered liquid glass refraction, the experience evokes the impenetrable permanence of a cold-storage vault balanced with the fluid precision of a high-frequency input instrument.

The design movement centers on hyper-polished glassmorphism suspended over an absolute void. High-contrast monochrome anchors the experience, establishing serious cryptographic authority, while precision specular highlights, optical rim lighting, and subtle coordinate grids convey mathematical rigor, absolute privacy, and instant responsiveness.

## Colors
The visual baseline is absolute true black (`#000000`), completely suppressing residual luminance on OLED hardware to preserve physical privacy and visual contrast.

### Neutral & Glass Surfaces
- **Canvas / Void:** `#000000` (Base ground)
- **Glass Fill:** `rgba(255, 255, 255, 0.06)` (Baseline floating substrate)
- **Surface Strong:** `rgba(255, 255, 255, 0.12)` (Elevated keys, active rows, dialog sheets)
- **Glass Border:** `rgba(255, 255, 255, 0.25)` (Defines structural boundaries)
- **Border Subtle:** `rgba(255, 255, 255, 0.10)` (Secondary dividers, key troughs)
- **Glass Highlight (Rim Light):** `rgba(255, 255, 255, 0.60)` (Top-edge specular sheen)

### Text Hierarchy
- **Text Primary:** `#FFFFFF` (100% pure contrast for titles, active key labels, and tokens)
- **Text Body:** `#CCCCCC` (Neutral clarity for general data readouts and fields)
- **Text Secondary:** `#999999` (Metadata, cryptographic hashes, protocol tags)
- **Text Muted:** `#666666` (Subtle placeholders, key subtext, timestamp markers)

### Semantic Signals
- **Success / Authenticated:** `#10B981` (Emerald green, restricted to valid entropy, verified handshakes, and unlocked states)
- **Danger / Destructive:** `#F43F5E` (Crimson red, reserved for vault purges, invalid cryptographic signatures, and key leak warnings)
- **Warning / Ambiguity:** `#CCCCCC` (Deliberate monochrome to avoid unnecessary emotional alarm during standard protocol notifications)

## Typography
Typography is tuned for technical legibility, rapid scannability under duress, and zero-distortion data rendering. Inter provides clean geometric architecture with uniform vertical proportions.

- **Headlines (`headline-lg`, `headline-md`, `headline-sm`):** Rendered in Bold or Semi-Bold with tight tracking for concise security prompts, biometric verification screens, and account identifiers.
- **Body (`body-lg`, `body-md`):** Uses regular weights with relaxed line height to ensure complex strings, security questions, and zero-knowledge disclosures remain fatigue-free.
- **Labels & Micro (`label-lg`, `label-md`, `micro`):** Slightly expanded tracking with Medium weights for system status, cryptographic key specs (e.g., `AES-GCM-256`), and keyboard cap characters. All alphanumeric keys enforce tabular figures (`tnum`) to eliminate spatial shifting during dynamic inputs.

## Layout & Spacing
Built upon an unyielding 4px spatial rhythm (`xs: 4px`, `sm: 8px`, `md: 12px`, `lg: 16px`, `xl: 24px`, `xxl: 32px`). This mathematical grid dictates all inner element padding, inter-key gaps, and structural stack offsets.

### Canvas & Breakpoints
- **Mobile First Focus (Keyboard & Vault Views):** Outer canvas margins are pegged at `16px` (`space-lg`), maximizing screen utility while maintaining safe touch targets. In keyboard viewports, gutters shrink to `4px` (`space-xs`) or `6px` between key centers to optimize finger landing mechanics.
- **Desktop & Vault Tablet (>=768px):** The interface scales to a fixed-width cryptographic cockpit (max `640px` for focused modal authentications, `960px` for multi-vault explorer views) centered with `24px` (`space-xl`) gutter spacing.

## Elevation & Depth
Elevation is constructed via optical refraction rather than diffuse drop shadows. Visual depth is established using a multi-layer composition:

1. **Ambient Precision Grid (Base):** A perpetual background coordinate matrix composed of 1px linear grid lines spaced exactly `64px` apart, rendered in `#FFFFFF` at `0.15` opacity against the pure `#000000` canvas.
2. **Backdrop Blur Filter:** Floating panels apply hardware-accelerated `backdrop-filter: blur(24px) saturate(180%)`.
3. **Glass Substrate:** Baseline fill using `rgba(255, 255, 255, 0.06)`.
4. **Specular Rim Light (Top Edge Highlight):** An inner gradient stroke along the top boundary: `linear-gradient(180deg, rgba(255, 255, 255, 0.60) 0%, rgba(255, 255, 255, 0.10) 60%, rgba(255, 255, 255, 0.00) 100%)` with a thickness of 1px.
5. **Perimeter Border:** Crisp `rgba(255, 255, 255, 0.25)` 1px outer stroke.
6. **Inner Glow Diffusion:** `box-shadow: inset 0 1px 1px 0 rgba(255, 255, 255, 0.20), 0 16px 32px -8px rgba(0, 0, 0, 0.80)`.
7. **Active Interaction Flare:** On physical touch or key-down, the substrate jumps instantly from `0.06` to `rgba(255, 255, 255, 0.18)`, triggering a local optical glow of `0 0 12px rgba(255, 255, 255, 0.40)`.

## Shapes
Shapes define the tactile nature of this design system, bifurcated strictly between functional controls and architectural envelopes:

- **Atomic Controls (`sm: 6px`, `md: 8px`, `lg: 12px`):** Used for tactile keyboard caps, entry fields, credential pills, and micro copy actions. Keys leverage `8px` corner radius to balance finger target area with perimeter separation.
- **Pill / Token Formats (`pill: 999px`):** Used for cipher status badges, zero-knowledge sync toggles, and master biometric triggers.
- **Liquid Glass Architecture (`24px`):** Reserved exclusively for high-level structures—vault cards, bottom drawer keyboards, confirmation sheets, and biometric prompt dialogs. This pronounced curve acts as the visual container for the refraction engine.

## Components

### Keyboard Surface & Keys
- **Chassis:** A singular `24px` top-radiused surface draped in Liquid Glass fill with specular top-rim lighting.
- **Keys:** Background `rgba(255, 255, 255, 0.08)`, radius `8px`, border `1px solid rgba(255, 255, 255, 0.15)`. Key character centered in `#FFFFFF` using `17px` Semi-Bold.
- **Pressed Key State:** Background scales to `rgba(255, 255, 255, 0.24)`, border expands to `rgba(255, 255, 255, 0.60)` with instant `0ms` touch response and a tactile `1px` downward displacement.

### Input Fields (Master Password / Passkey Seed)
- **Resting:** Background `rgba(255, 255, 255, 0.04)`, border `1px solid rgba(255, 255, 255, 0.15)`, radius `12px`, text `#FFFFFF`, caret `#FFFFFF`.
- **Focused:** Border transitions to pure `rgba(255, 255, 255, 0.60)` with a subtle inner glow `inset 0 0 8px rgba(255, 255, 255, 0.10)`. Monospaced characters for masked entropy dots.

### Interactive Buttons
- **Primary / Authorize:** Background `#FFFFFF`, label `#000000` (Bold `14px`), radius `12px`. Hover/Active triggers `opacity: 0.90` and a specular back-glow.
- **Liquid / Secondary:** Background `rgba(255, 255, 255, 0.06)`, border `1px solid rgba(255, 255, 255, 0.25)`, label `#FFFFFF`. Active state uses `rgba(255, 255, 255, 0.14)`.
- **Destructive Vault Purge:** Border `1px solid #F43F5E`, label `#F43F5E`, background `rgba(244, 63, 94, 0.08)`.

### Vault Entry Cards
- Encased in `24px` radius Liquid Glass with the full 7-layer optical stack.
- Top section houses the service monogram in a micro glass square (`8px` radius) alongside primary identity titles (`#FFFFFF`, Semi-Bold `17px`) and secondary username metadata (`#999999`, `13px`).
- Bottom section provides concealed credential readouts masked by uniform bullet glyphs (`••••••••`) rendered in `#666666`.

### Entropy Chips & Protocol Badges
- Pill-shaped (`999px`), padding `4px 10px`.
- High-security indicator: Emerald pulse dot (`#10B981`) paired with `micro` uppercase typography (`#FFFFFF`).
- Weak entropy / protocol alert: Red indicator dot (`#F43F5E`).

### Checkboxes & Segmented Switches
- **Checkboxes:** `8px` radius glass squares. Unselected state is bordered with `rgba(255, 255, 255, 0.25)`; selected state fills with `#FFFFFF` and punches through an inverted `#000000` check icon.
- **Segmented Vault Selector:** Encapsulated pill track (`rgba(255, 255, 255, 0.04)`) with an active sliding pill segment utilizing `rgba(255, 255, 255, 0.15)` and a sharp `0.5px` top-rim reflection.