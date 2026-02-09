# Ova Frontend Redesign Plan

> **Authors**: Design Principles Agent + Competitor Analysis Agent
> **Date**: 2026-02-09
> **Status**: Approved - Ready for implementation
> **Design Identity**: "Quiet Confidence, Institutional Precision"

This is the definitive guide for redesigning Ova's frontend. It combines design principles research (Apple, Dieter Rams, 2026 fintech trends) with competitive analysis (Wise, Revolut, N26, Nubank, Papara, Midas, Monzo) into actionable specifications.

---

## 1. Design Philosophy

### Core Identity

Ova is not a startup. Ova is not a legacy bank. Ova is a **modern financial institution** specializing in the Turkey-EU corridor.

The design must communicate:

| Attribute | What it means | How it manifests |
|-----------|---------------|-----------------|
| **Confident** | We don't need to prove ourselves with flashy design | Massive whitespace, restrained color, typography-led hierarchy |
| **Precise** | Every element is measured and intentional | Systematic spacing, consistent tokens, no ad-hoc styling |
| **Transparent** | Fees, rates, and status are always visible | FX calculator, transfer progress tracker, clear fee breakdowns |
| **Trustworthy** | Regulated, secure, and reliable | Compliance badges, security indicators, designed error states |
| **Calm** | No urgency, no hype, no marketing gimmicks | Subtle motion, neutral palette, functional color only |

### Brand Voice

> Ova's design communicates quiet confidence -- the assurance that comes from institutional precision. Every element is measured, every interaction is purposeful, and every detail serves the user's financial goals. We don't need to impress; we need to reassure.

### Guiding Principles

1. **Less, but better** (Rams #10) -- Every element must justify its existence. When in doubt, remove.
2. **Honest** (Rams #6) -- Never mislead about fees, rates, timing, or capabilities.
3. **Clarity first** (Apple) -- Understandable at a glance. No instructions needed.
4. **Content is king** (Apple) -- The interface defers to the content: money, balances, rates, status.
5. **Design all states** (Rams #8) -- Loading, empty, error, and success states get equal design attention.
6. **Useful over beautiful** (Rams #2) -- A live FX calculator is worth more than a decorative illustration.
7. **Long-lasting** (Rams #7) -- No trendy patterns. Classic, neutral, timeless.

### What We're NOT

- Not a SaaS landing page (no grid backgrounds, no floating blurs, no chip badges)
- Not a startup demo (no "scroll stories", no phone mockups with fake data)
- Not a crypto product (no dark mode by default, no neon accents, no glassmorphism)
- Not a legacy bank (no stock photos of handshakes, no "We care about you" hero copy)

---

## 2. Design System Specification

### 2.1 Color Tokens

```css
/* === BRAND === */
--ova-navy:      #0D1B2A;   /* Primary brand. Logo, sidebar, hero sections */
--ova-navy-light: #1B2D3E;  /* Hover state for navy elements */

/* === INTERACTIVE === */
--ova-blue:      #1A6FD4;   /* Links, active tabs, focus rings, interactive accents */
--ova-blue-hover: #1560B8;  /* Hover state for blue elements */
--ova-blue-light: #EBF4FF;  /* Blue tinted backgrounds (selected states) */

/* === NEUTRAL === */
--ova-950:  #0A0A0A;   /* Darkest text (rare, for max contrast) */
--ova-900:  #171717;   /* Primary text */
--ova-700:  #404040;   /* Secondary text, labels */
--ova-500:  #737373;   /* Placeholder text, tertiary content */
--ova-400:  #A3A3A3;   /* Disabled text */
--ova-300:  #D4D4D4;   /* Borders */
--ova-200:  #E5E5E5;   /* Subtle borders, dividers */
--ova-100:  #F5F5F5;   /* Subtle backgrounds */
--ova-50:   #FAFAFA;   /* Page background */
--ova-white: #FFFFFF;  /* Cards, inputs, modals */

/* === FUNCTIONAL (state communication only) === */
--ova-green:      #16803C;   /* Success, positive amounts, completed */
--ova-green-light: #F0FDF4;  /* Success backgrounds */
--ova-red:        #DC2626;   /* Errors, negative amounts, failed */
--ova-red-light:  #FEF2F2;   /* Error backgrounds */
--ova-amber:      #B45309;   /* Warnings, pending, expiring */
--ova-amber-light: #FFFBEB;  /* Warning backgrounds */
```

**Rules:**
- Navy is for IDENTITY: logo, sidebar, occasional section backgrounds. Never for decoration.
- Blue is for INTERACTION: anything clickable, focusable, or selected.
- Green/Red/Amber are for STATE: only used to communicate status.
- No gradients. No teal. No decorative color.
- All functional colors must maintain 4.5:1 contrast ratio against their background.

### 2.2 Typography

```css
/* === FONT FAMILY === */
font-family: 'Inter', -apple-system, 'SF Pro Display', system-ui, sans-serif;

/* === LOGO === */
.ova-logo {
  font-family: 'Inter', sans-serif;
  font-weight: 700;
  letter-spacing: -0.06em;
  color: var(--ova-navy);
  text-transform: lowercase;
}

/* === TYPE SCALE (Major Third - 1.25 ratio) === */
--text-display:   48px / 1.0  / -0.025em / 600  /* Hero headlines only */
--text-h1:        36px / 1.15 / -0.02em  / 600  /* Page titles */
--text-h2:        28px / 1.2  / -0.015em / 600  /* Section titles */
--text-h3:        20px / 1.3  / -0.01em  / 500  /* Card headers, sub-sections */
--text-body-lg:   17px / 1.6  / 0        / 400  /* Hero body, important paragraphs */
--text-body:      15px / 1.6  / 0        / 400  /* Default body text */
--text-body-sm:   13px / 1.5  / 0        / 400  /* Secondary text, descriptions */
--text-caption:   11px / 1.4  / 0.02em   / 500  /* Labels, badges, overlines */
--text-mono:      13px / 1.5  / 0        / 400  /* IDs, hashes, account numbers */
  font-family: 'JetBrains Mono', 'SF Mono', monospace;

/* === MONETARY AMOUNTS === */
/* Amounts are ALWAYS semibold (600) or bold (700), never regular weight */
/* This is a key fintech pattern from Wise/Revolut */
.amount { font-weight: 600; font-variant-numeric: tabular-nums; }
.amount-display { font-size: var(--text-display); font-weight: 600; }
```

**Rules:**
- ONE font family (Inter) for everything. No serif. No mixing.
- Every text element maps to exactly one token. No ad-hoc sizing.
- Monetary amounts are ALWAYS semibold minimum (`font-weight: 600`).
- Use `tabular-nums` for all monetary values (consistent digit widths).
- Labels and badges use `text-caption` with `tracking-wide` and `uppercase`.
- Body text never goes below 13px.

### 2.3 Spacing System

```css
/* 4px base unit, 8px comfortable grid */
--space-0.5:  2px;    /* Micro: icon-to-text gap */
--space-1:    4px;    /* Tight: related inline elements */
--space-2:    8px;    /* Compact: pill padding, tight groups */
--space-3:    12px;   /* Standard: label-to-input, list item gaps */
--space-4:    16px;   /* Comfortable: between form fields */
--space-5:    20px;   /* Relaxed: between form fields (preferred) */
--space-6:    24px;   /* Card padding (standard) */
--space-8:    32px;   /* Card padding (generous), section internal */
--space-10:   40px;   /* Between card groups */
--space-12:   48px;   /* Section headers to content */
--space-16:   64px;   /* Between major page sections */
--space-20:   80px;   /* Landing page section separation */
--space-24:   96px;   /* Hero section padding */
```

**Layout constraints:**
- Max content widths: `720px` (forms), `960px` (dashboard), `1200px` (landing)
- Card padding: Always `24px` or `32px`
- Form field spacing: `20px` between fields, `12px` between label and input
- Interactive elements: min `44px` height (touch target)
- Input/button height: `48px` standard

### 2.4 Component Specifications

#### Buttons

```
PRIMARY
  bg: var(--ova-navy)  |  text: white  |  rounded-xl  |  h-12  |  px-6
  hover: var(--ova-navy-light), shadow-sm
  active: scale(0.98) for 100ms
  disabled: bg-ova-300, text-ova-500, cursor-not-allowed
  focus: ring-2 ring-ova-blue ring-offset-2

SECONDARY
  bg: white  |  border: var(--ova-300)  |  text: var(--ova-900)  |  rounded-xl  |  h-12  |  px-6
  hover: bg-ova-50, border-ova-400
  active: bg-ova-100
  disabled: bg-ova-50, text-ova-400, border-ova-200

GHOST
  bg: transparent  |  text: var(--ova-700)
  hover: bg-ova-100
  active: bg-ova-200

DANGER
  bg: var(--ova-red)  |  text: white  |  rounded-xl  |  h-12  |  px-6
  hover: #B91C1C (darker red)

LINK
  color: var(--ova-blue)  |  underline on hover
  font-weight: 500
```

#### Inputs

```
DEFAULT
  bg: white  |  border: var(--ova-300)  |  rounded-xl  |  h-12  |  px-4
  text: var(--ova-900)  |  placeholder: var(--ova-500)
  focus: border-ova-blue, ring-2 ring-ova-blue/20

ERROR
  border: var(--ova-red)  |  ring-2 ring-ova-red/20
  + inline error message below in text-body-sm, color: var(--ova-red)

DISABLED
  bg: var(--ova-100)  |  text: var(--ova-500)  |  cursor-not-allowed

LABELS
  text-body-sm  |  font-medium  |  color: var(--ova-700)
  margin-bottom: 12px (space-3)
```

#### Cards

```
DEFAULT
  bg: white  |  border: var(--ova-200)  |  rounded-2xl  |  p-6 or p-8
  shadow: 0 1px 3px rgba(0,0,0,0.04)
  NO hover effects (cards in banking should feel stable, not bouncy)

WITH HEADER
  Header area: px-6 py-4, border-b border-ova-100
  Header text: text-h3, color: var(--ova-900)

BALANCE CARD (special)
  bg: white  |  border-l-4 border-ova-navy  |  rounded-2xl  |  p-6
  Layout (top to bottom):
    Currency label: text-caption, uppercase, color: var(--ova-500)  e.g. "TRY"
    Balance number: text-display, font-semibold, color: var(--ova-navy)  e.g. "₺248,420.12"
    Subtitle: text-body-sm, color: var(--ova-500)  e.g. "Available balance"
  Currency label appears ABOVE the number, not inline (Revolut/Wise pattern)
  shadow: 0 2px 8px rgba(0,0,0,0.06)
```

#### Status Pills

```
SUCCESS:   bg: var(--ova-green-light)  |  text: var(--ova-green)  |  font-medium
WARNING:   bg: var(--ova-amber-light)  |  text: var(--ova-amber)  |  font-medium
ERROR:     bg: var(--ova-red-light)    |  text: var(--ova-red)    |  font-medium
INFO:      bg: var(--ova-blue-light)   |  text: var(--ova-blue)   |  font-medium
NEUTRAL:   bg: var(--ova-100)          |  text: var(--ova-700)    |  font-medium

All pills: text-caption, rounded-full, px-2.5 py-1
```

### 2.5 Icon System

**Recommendation**: [Lucide Icons](https://lucide.dev/)
- Open source, MIT license
- Consistent 24px grid, 1.5px stroke
- Excellent coverage of fintech-relevant icons
- Tree-shakeable (only bundle what you use)

**Sidebar navigation icons:**
| Item | Lucide Icon | Replaces |
|------|-------------|----------|
| Home | `Home` | ◉ |
| Transfer | `ArrowUpRight` | ↗ |
| Accounts | `Wallet` | ▤ |
| History | `Clock` | ☰ |
| Settings | `Settings` | ⚙ |

### 2.6 Motion Guidelines

```css
/* === TRANSITIONS === */
--transition-fast:  150ms ease-out;   /* Hover states, focus rings */
--transition-base:  200ms ease-out;   /* Color changes, border changes */
--transition-slow:  300ms ease-out;   /* Modals, drawers */

/* === PAGE LOAD (landing page only) === */
@keyframes hero-enter {
  from { opacity: 0; }
  to   { opacity: 1; }
}
.hero-content {
  animation: hero-enter 400ms ease-out;
}

/* === LOADING === */
/* Thin indeterminate progress bar at top of viewport (like YouTube/GitHub) */
/* Skeleton screens matching final layout shape for content areas */
/* No spinning circles. No bouncing dots. */

/* === REDUCED MOTION === */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

**Rules:**
1. No scroll-reveal animations. Content is just THERE.
2. No parallax. No floating elements.
3. Interactive transitions < 200ms.
4. Only landing page hero gets a fade-in (400ms, page load only, not scroll).
5. Success confirmations: subtle checkmark animation (scale from 0 to 1, 300ms).
6. Everything respects `prefers-reduced-motion`.

---

## 3. Page-by-Page Redesign Plan

### 3.1 Landing Page

**Current state**: SaaS startup aesthetic (grid background, blur circles, phone mockup, scroll story, capability cards).

**Redesigned structure**:

```
┌────────────────────────────────────────────────────────┐
│ NAV: [ova logo]                          [Sign in]     │
│ Clean, transparent bg, border-bottom only              │
│ No dropdown menus. Just logo + sign in.                │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ HERO SECTION (generous padding: 96px top, 80px bottom) │
│                                                        │
│ Left column (55%):                                     │
│   "Cross-border transfers                              │
│    between Turkey and Europe.                          │
│    Instant. Transparent. Secure."                      │
│                                                        │
│   One paragraph of substance (max 2 sentences).        │
│   "Move money between TRY and EUR with live rates,     │
│    transparent fees, and real-time settlement tracking."│
│                                                        │
│   [Open an account]  (primary button)                  │
│   "Already have an account? Sign in" (text link)       │
│                                                        │
│ Right column (45%):                                    │
│   ┌──────────────────────────────────────┐             │
│   │ FX CALCULATOR WIDGET                 │             │
│   │                                      │             │
│   │ You send         TRY  [10,000.00  ]  │             │
│   │ They receive     EUR  [  271.00   ]  │             │
│   │                                      │             │
│   │ Rate: 1 TRY = 0.0271 EUR             │             │
│   │ Fee: TRY 25.00 (0.25%)               │             │
│   │ Arrives: ~2 minutes                  │             │
│   │                                      │             │
│   │ [Send this amount →]                 │             │
│   └──────────────────────────────────────┘             │
│                                                        │
│ Background: white or very subtle ova-50. NO gradients, │
│ NO grid, NO blur circles. Pure whitespace.             │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ TRUST BAR (centered strip, subtle ova-100 background)  │
│                                                        │
│ [BDDK Licensed]  [PSD2 Authorized]  [KVKK Compliant]  │
│                                                        │
│ Small regulatory text/logos. Not flashy. Factual.      │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ VALUE PROPOSITIONS (stacked vertically, max-w-720px)   │
│ Section padding: 80px top/bottom                       │
│                                                        │
│ "Turkey-EU in seconds"                                 │
│ Send TRY, receive EUR. Real exchange rates,            │
│ no hidden markups. Settlement confirmed on-chain.      │
│                                                        │
│ ─────────────────────────── (subtle divider)           │
│                                                        │
│ "Transparent fees, always"                             │
│ See exactly what you pay before you send.              │
│ No surprises. Rate locks for 30 seconds.               │
│                                                        │
│ ─────────────────────────── (subtle divider)           │
│                                                        │
│ "Bank-grade security"                                  │
│ Two-factor auth, KYC verification, sanctions           │
│ screening, and encrypted infrastructure.               │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ SOCIAL PROOF (optional, if data available)             │
│                                                        │
│ "X transfers completed"  "Average Y minutes"           │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ FINAL CTA                                              │
│ Centered, generous whitespace around it                │
│                                                        │
│ "Ready to move money without borders?"                 │
│ [Open an account]                                      │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ FOOTER                                                 │
│ [ova]  Designed for trust. Built for movement.         │
│                                                        │
│ Security · Compliance · Terms · Contact                │
│                                                        │
│ Ova Financial Services, licensed by BDDK (Turkey)      │
│ and authorized under PSD2 (European Union).            │
│ KVKK and GDPR compliant.                              │
└────────────────────────────────────────────────────────┘
```

**What was REMOVED** (vs current):
- Grid background pattern
- Floating blur circles (2 decorative gradients)
- Phone mockup with 4 scenes
- "Scroll story" section with IntersectionObserver
- Capability cards grid (6 cards)
- "next-generation money operations" chip badge
- Serif italic display font usage
- Progress bar animations
- All decorative SVGs

**What was ADDED**:
- Live FX calculator widget (interactive, shows real value)
- Regulatory trust bar (BDDK, PSD2, KVKK)
- Stacked value propositions with substance
- Clear, single CTA path
- Regulatory disclosures in footer

### 3.2 Auth Pages (Login / Signup)

**Layout**: Centered form, max-w-md, generous vertical padding.

**Login page changes**:
- Logo: lowercase "ova" in navy, using `.ova-logo` class (consistent with landing)
- Remove always-visible 2FA field. Show ONLY after server responds that 2FA is required (two-step flow)
- Input height: 48px (`h-12`)
- Focus ring: blue, not black (`focus:ring-ova-blue/20`)
- Submit button: full-width, navy background, 48px height
- Error messages: inline per-field where possible, banner for server errors
- Add subtle trust signal below form: "Secured with bank-grade encryption" in text-caption
- "Forgot password?" and "Sign up" links in text-body-sm, ova-blue color

**Signup page changes**:
- Same layout refinements as login
- Region selector: Use flag icons (Turkish flag + EU flag) alongside text, not just plain dropdown
- Consider progressive registration: email -> verify -> details (Revolut pattern, can be Phase 2)
- Password requirements: show inline as user types (min 8 chars indicator)

### 3.3 Dashboard Home

**Layout**: `max-w-960px`, sidebar on left.

```
┌─────────────────────────────────────────────────────┐
│ "Good morning, Alikan"              February 9, 2026│
│                                                     │
│ ┌──────────────────────┐ ┌──────────────────────┐   │
│ │ TRY (caption, gray)  │ │ EUR (caption, gray)  │   │
│ │ ₺248,420.12 (display)│ │ €12,350.00 (display) │   │
│ │ Available balance     │ │ Available balance     │   │
│ │ border-l-4 navy      │ │ border-l-4 navy      │   │
│ └──────────────────────┘ └──────────────────────┘   │
│                                                     │
│ ┌──────────────────────────────────────────────┐    │
│ │ Quick Actions (horizontal icon strip)        │    │
│ │ [↗ Send]  [↙ Request]  [⇄ Convert]          │    │
│ └──────────────────────────────────────────────┘    │
│                                                     │
│ Recent Activity                                     │
│ ┌──────────────────────────────────────────────┐    │
│ │ ↗  P2P Transfer to Mehmet   -₺5,000   Feb 8 │    │
│ │ ↙  Deposit                  +₺10,000  Feb 7 │    │
│ │ ⇄  FX Conversion           -₺50,000  Feb 6 │    │
│ │                             +€1,355   Feb 6 │    │
│ │ View all transactions →                      │    │
│ └──────────────────────────────────────────────┘    │
│                                                     │
│ ┌──────────────────────────────────────────────┐    │
│ │ Account Health                               │    │
│ │ KYC: Verified ✓   2FA: Enabled ✓            │    │
│ │ Pending transfers: 0                         │    │
│ └──────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

**Key changes**:
- Personalized greeting with user's first name
- Balance cards: white bg, navy left border accent, display-size semibold numbers
- Balance is THE hero -- largest visual element on the page
- Quick actions: icon-based strip, not plain text buttons
- Recent activity: shows amounts (+/-), counterparty info, and dates
- Account health section: KYC, 2FA, pending items at a glance

### 3.4 Transfer Flow

**Domestic transfer**: Clean wizard (same tab structure, refined visuals).

**Cross-border transfer** (the flagship experience):

```
STEP 1: Amount Input
┌──────────────────────────────────────────┐
│                                          │
│    You send                              │
│                                          │
│         ₺ 120,000.00                     │
│    (display-size, grows as user types)   │
│                                          │
│    From: TRY Wallet (₺248,420 available) │
│    To: [Recipient account ID         ]   │
│                                          │
│    [Get quote →]                         │
└──────────────────────────────────────────┘

STEP 2: FX Quote Review
┌──────────────────────────────────────────┐
│                                          │
│  You send          They receive          │
│  ₺120,000.00   →   €3,252.00            │
│                                          │
│  ─────────────────────────────────────   │
│  Exchange rate    1 TRY = 0.0271 EUR     │
│  Fee              ₺300.00 (0.25%)        │
│  Arrives in       ~2 minutes             │
│  ─────────────────────────────────────   │
│                                          │
│  Quote expires in:  [27s] (circular)     │
│  (turns amber <10s, red <5s)             │
│                                          │
│  [Cancel]         [Confirm & send]       │
└──────────────────────────────────────────┘

STEP 3: Transfer Progress (POST-CONFIRMATION)
┌──────────────────────────────────────────┐
│                                          │
│  Sending ₺120,000.00 → €3,252.00        │
│                                          │
│  ● Payment initiated        ✓ Complete   │
│  │                                       │
│  ● Compliance check         ✓ Passed     │
│  │                                       │
│  ● FX conversion            ✓ Complete   │
│  │                                       │
│  ● Settlement               ◐ In progress│
│  │  Burn: confirmed                      │
│  │  Bridge relay: in transit             │
│  │  Mint: pending                        │
│  │                                       │
│  ○ Delivered                ○ Pending     │
│                                          │
│  This maps to ICTT bridge status:        │
│  burn → relay → mint → complete          │
│                                          │
└──────────────────────────────────────────┘

STEP 4: Success
┌──────────────────────────────────────────┐
│                                          │
│           ✓ (animated checkmark)         │
│                                          │
│    Transfer complete                     │
│    €3,252.00 delivered                   │
│                                          │
│    Payment ID: abc-123-def               │
│    Completed: Feb 9, 2026, 14:32         │
│                                          │
│    [Make another transfer]               │
│    [View in history]                     │
│                                          │
└──────────────────────────────────────────┘
```

#### ICTT Bridge Progress Tracker -- Detailed Specification

**This is Ova's killer feature visualization.** No competitor shows real blockchain settlement status. This component should be reusable for both the transfer detail view AND the dashboard "active transfers" widget.

**5-step timeline:**

| Step | Label | Maps to Backend | Status Indicators |
|------|-------|----------------|-------------------|
| 1 | Payment initiated | Payment order created | Gray circle -> Blue pulsing -> Green checkmark |
| 2 | Compliance check | Sanctions + limit checks | Same indicator pattern |
| 3 | FX conversion | Quote locked + ledger posting | Same indicator pattern |
| 4 | Settlement | ICTT bridge (burn -> relay -> mint) | Same, with expandable sub-steps |
| 5 | Delivered | Callback confirmed | Same |

**Visual design:**
- Vertical timeline with connecting line between steps
- Step indicators: completed = green checkmark (Lucide `Check`), active = blue pulsing dot (CSS animation), pending = gray circle
- Each completed step shows timestamp (e.g., "14:32:05")
- Active step shows elapsed time ("12 seconds ago")
- The Settlement step (4) can optionally expand to show blockchain details:
  - Burn tx hash (truncated, with copy button)
  - Bridge relay status
  - Mint tx hash (when available)
  - Block numbers
- Expandable details use `text-mono` (JetBrains Mono) for hashes
- Collapse/expand toggle: "Show settlement details" / "Hide settlement details"

**Important UX rule**: User-facing labels say "Settlement" not "mint/burn". Blockchain terminology is hidden behind the expandable detail section for power users. Regular users see: "Settlement -- in progress" or "Settlement -- complete."

**Component**: `web/components/ui/TransferProgress.tsx`
- Props: `steps: { label, status, timestamp?, details? }[]`
- Reusable across transfer detail view AND dashboard active transfers widget

### 3.5 Accounts Page

**Changes**:
- Account cards show: currency, IBAN (masked, with copy button), balance (semibold), status pill
- Wallet address shown in collapsible section for blockchain-linked accounts
- Create account: guided flow with explanation of account types
- Remove raw UUID display -- use masked IBAN as the primary identifier

### 3.6 History Page

**Changes**:
- Transaction rows show: icon (type), description, counterparty, amount (+/- with color), date
- Filter bar: by type, status, date range (not just account selector)
- Amounts: green for credits, default color for debits
- Click-to-expand for transaction details (reference ID, settlement hash, etc.)
- Pagination with "Load more" pattern (not numbered pages)

### 3.7 Settings Page

**Changes**:
- Tabbed or accordion sections: Profile, Security, Preferences
- **Profile**: Name fields, email (read-only), phone (read-only), KYC status badge, region
- **Security**:
  - 2FA setup: proper modal with QR code display (REMOVE `alert()`)
  - Password change form
  - Active sessions list (if available from backend)
- **Preferences**: Language (TR/EN), currency display format
- All forms use refined input style (48px height, blue focus rings)

### 3.8 KYC Onboarding

**Changes**:
- Stepper: horizontal with clear labels + estimated time ("~5 minutes")
- Frame verification positively: "Verify your identity to unlock international transfers up to EUR 50,000"
- Remove emoji icons (✓, ⏳, ✕) -- use Lucide icons or styled status indicators
- Provider integration: full-screen modal for Veriff/Onfido widget
- Status states:
  - Pending: "We're reviewing your documents. This usually takes a few minutes."
  - Approved: Green checkmark with feature unlock list
  - Rejected: Clear next steps, retry button, support contact

---

## 4. Navigation & Layout

### Sidebar (Dashboard)

```
┌──────────────────────┐
│ ova (logo, navy)     │
│──────────────────────│
│                      │
│ 🏠  Home             │  (Lucide: Home)
│ ↗   Transfer         │  (Lucide: ArrowUpRight)
│ 💳  Accounts         │  (Lucide: Wallet)
│ ⏱   History          │  (Lucide: Clock)
│                      │
│──────────────────────│
│ ⚙   Settings         │  (Lucide: Settings)
│                      │
│──────────────────────│
│                      │
│ (no version tag)     │
└──────────────────────┘
```

- Background: `var(--ova-navy)` (#0D1B2A)
- Active item: left accent bar (3px, white) + white text
- Inactive items: rgba(255,255,255,0.6), hover: rgba(255,255,255,0.85)
- Width: 240px (slightly narrower than current 256px)
- Icons: Lucide, 20px size, 1.5px stroke
- Collapsible on tablet viewports

### Header Bar

```
┌──────────────────────────────────────────────────────┐
│                                    🔔  [AK avatar]   │
│                                    notifications +   │
│                                    user dropdown     │
└──────────────────────────────────────────────────────┘
```

- Height: 64px
- Background: white, border-bottom: ova-200
- Right side: notification bell + user initials circle + dropdown

### Mobile Responsive

- Below 768px: sidebar collapses to bottom tab bar (5 items)
- Content area becomes full-width
- Cards stack vertically
- Input heights maintained at 48px (touch-friendly)

---

## 5. Trust & Compliance Signals

### Where trust signals appear

| Location | Signal |
|----------|--------|
| Landing footer | BDDK license number, PSD2 authorization, KVKK compliance statement |
| Landing trust bar | Regulatory badges (BDDK, PSD2, KVKK logos) |
| Auth pages | "Secured with bank-grade encryption" below form |
| Transfer confirmation | "Your transfer is protected by..." |
| Dashboard footer | Small regulatory disclaimer |
| Settings | KYC verification status, account tier |

### Specific Regulatory Badges

**Turkey:**
| Regulator | Badge Text | Context |
|-----------|-----------|---------|
| BDDK | "Licensed by BDDK" | Banking Regulation and Supervision Agency |
| MASAK | "MASAK Compliant" | Financial Crimes Investigation Board (AML) |
| KVKK | "KVKK Compliant" | Personal Data Protection (Turkey's GDPR equivalent) |

**European Union:**
| Regulator | Badge Text | Context |
|-----------|-----------|---------|
| PSD2 | "PSD2 Authorized" | Payment Services Directive |
| AML5/6 | "AML Compliant" | Anti-Money Laundering Directives |
| GDPR | "GDPR Compliant" | General Data Protection Regulation |

**Placement:**
- **Landing page trust bar**: All 6 badges in a horizontal strip, monochrome, centered
- **Landing page footer**: Full regulatory disclosure text with license numbers
- **Dashboard footer** (all pages): Condensed single-line regulatory statement
- **Auth pages**: "Secured with bank-grade encryption" below form
- **Transfer confirmation**: "Your transfer is protected by BDDK and PSD2 regulations"

### How compliance is displayed

- Regulatory badges: small, monochrome, professional (not colorful logos)
- Badge style: `text-caption`, `color: var(--ova-500)`, optional subtle border
- License text: `text-caption`, `color: var(--ova-500)`
- Security indicators on forms: small lock icon (Lucide `Shield`) + text, subtle gray
- Never prominent enough to create anxiety, always present enough to build confidence

---

## 6. Do's and Don'ts

### DO

- Use whitespace as a primary design element (40%+ of page area)
- Let typography carry hierarchy (size > weight > color)
- Keep interactive elements >= 44px height (48px preferred)
- Use functional color only (green/red/amber for state, blue for interactive)
- Design every state (loading = skeleton, empty = CTA, error = recovery, success = confirmation)
- Use consistent spacing from the token system
- Show monetary amounts in semibold with tabular-nums
- Display regulatory/compliance information on every page
- Use Lucide icons consistently at 20px
- Test with real Turkish and European text/numbers
- Show real numbers/data wherever possible (rates, speeds, fees) -- data builds trust more than words
- Show what features unlock after KYC (progressive disclosure motivates completion)
- Display TRY as the primary currency for Turkish users, EUR for EU users (region-aware defaults)
- Use "Settlement" for end users when referring to blockchain operations
- Support locale-aware number formatting: Turkish uses periods for thousands, commas for decimals (₺248.420,12 not ₺248,420.12). Use `tr-TR` locale for TRY amounts, `de-DE` or appropriate EU locale for EUR amounts -- never hardcode `en-US`

### DON'T

- Don't use gradients for decoration
- Don't use background patterns or textures
- Don't use floating/blurred decorative elements
- Don't use more than 2 font weights on a single screen
- Don't use animations longer than 300ms (except hero fade-in)
- Don't use parallax or scroll-driven animations
- Don't use colored backgrounds on cards (white only, with optional border accent)
- Don't use serif fonts anywhere
- Don't use uppercase text except for labels/badges at text-caption size
- Don't use emoji or Unicode characters as icons
- Don't use `alert()` or `confirm()` browser dialogs
- Don't use translateY hover effects on cards
- Don't hardcode hex colors -- always use tokens
- Don't mix rounded-lg and rounded-xl -- use rounded-xl (16px) for all containers, rounded-full for pills
- Don't use marketing buzzwords ("next-generation", "revolutionary", "disrupting") -- be factual
- Don't expose blockchain/crypto terminology to end users (say "settlement" not "mint/burn", "confirmed" not "on-chain")
- Don't show raw UUIDs to users -- use masked IBANs or short reference codes

---

## 7. Implementation Priority

| Priority | Page/Component | Impact | Effort | Dependencies |
|----------|---------------|--------|--------|-------------|
| **P1** | Design system tokens (CSS variables, Tailwind config) | Foundation for everything | Medium | None |
| **P2** | Component library (Button, Input, Card, StatusPill, Icon) | Used everywhere | Medium | P1 |
| **P3** | Landing page redesign (with FX calculator) | First impression, conversion | High | P1, P2 |
| **P4** | Sidebar + Header + Dashboard layout | Daily user experience | Medium | P1, P2 |
| **P5** | Auth pages (Login, Signup, Forgot/Reset) | Trust gate | Low-Medium | P1, P2 |
| **P6** | Dashboard home (balances, activity, quick actions) | Core daily view | Medium | P4 |
| **P7** | Transfer flow (amount input, FX quote, progress tracker) | Revenue-critical | High | P2, P6 |
| **P8** | KYC onboarding | Conversion-critical | Medium | P2 |
| **P9** | Accounts, History, Settings | Supporting pages | Low | P2, P4 |

**Estimated phases**:
- **Phase A** (P1-P2): Design system foundation -- 2-3 days
- **Phase B** (P3-P5): Landing + Auth + Layout shell -- 3-4 days
- **Phase C** (P6-P8): Core dashboard features -- 4-5 days
- **Phase D** (P9): Supporting pages -- 2-3 days

---

## 8. Files to Modify

| File | Changes |
|------|---------|
| `web/tailwind.config.ts` | Replace color palette, update font family, add design tokens |
| `web/app/globals.css` | Replace all `.ova-*` custom classes with new token-based system |
| `web/app/layout.tsx` | Add Inter font import, update metadata |
| `web/app/page.tsx` | Complete rewrite: remove SaaS patterns, add FX calculator, trust signals |
| `web/components/ui/Button.tsx` | Update variants to use new color tokens, rounded-xl, h-12 |
| `web/components/ui/Input.tsx` | Update to h-12, rounded-xl, blue focus ring |
| `web/components/ui/Card.tsx` | Update borders, remove hover translateY, add BalanceCard variant |
| `web/components/layout/Sidebar.tsx` | Navy background, Lucide icons, refined active states |
| `web/app/(auth)/login/page.tsx` | Conditional 2FA, trust signal, refined inputs |
| `web/app/(auth)/signup/page.tsx` | Flag region selector, refined inputs |
| `web/app/(dashboard)/home/page.tsx` | Personalized greeting, balance display, activity redesign |
| `web/app/(dashboard)/transfer/page.tsx` | Large amount input, FX quote redesign, progress tracker |
| `web/app/(dashboard)/accounts/page.tsx` | IBAN display, improved cards |
| `web/app/(dashboard)/history/page.tsx` | Amount column, expanded filtering, better rows |
| `web/app/(dashboard)/settings/page.tsx` | Tabbed sections, remove alert(), proper 2FA modal |
| `web/app/(onboarding)/kyc/page.tsx` | Improved stepper, remove emoji, positive framing |
| NEW: `web/components/ui/StatusPill.tsx` | Reusable status indicator component |
| NEW: `web/components/ui/Skeleton.tsx` | Loading skeleton component |
| NEW: `web/components/ui/FxCalculator.tsx` | Landing page FX calculator widget |
| NEW: `web/components/ui/TransferProgress.tsx` | ICTT bridge status tracker |

---

## References

### Design Principles Sources
- Apple Human Interface Guidelines
- Dieter Rams' 10 Principles of Good Design
- 2026 Fintech Web Design Trends (Veza Digital)

### Competitor References
- Wise (wise.com) -- FX transparency, transfer progress, landing page
- Revolut (revolut.com) -- Amount input, currency display, motion
- N26 (n26.com) -- Web-first excellence, German design discipline, compliance display
- Nubank (nubank.com.br) -- Single brand color, design system rigor, warm minimalism
- Papara (papara.com) -- Turkish market patterns, simplicity expectations
- Midas (getmidas.com) -- Professional aesthetic, financial data display
- Monzo (monzo.com) -- Personality balance, notification patterns

---

*This document represents the consensus of both the Design Principles and Competitor Analysis agents. It should serve as the definitive guide for implementing Ova's frontend redesign.*
