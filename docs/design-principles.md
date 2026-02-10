# Ova Design Principles & Redesign Proposals

> **Author**: Design Principles Agent
> **Date**: 2026-02-09
> **Status**: Draft - Awaiting competitor-analyst feedback

---

## Part 1: Design Philosophy Foundation

### Apple's Design Philosophy Applied to Fintech

Apple's design principles center on three pillars that are directly transferable to Ova:

1. **Clarity**: Interfaces should be understandable at a glance. Every element must have a clear purpose. In banking, this means the user should never wonder "what happens if I tap this?"

2. **Deference**: The interface should defer to content. In Ova's case, the content IS the money -- balances, transfers, rates. The chrome around it should be invisible.

3. **Depth**: Visual layering gives users a sense of place. Where am I? Where did I come from? What can I do next? This is critical during multi-step flows like cross-border transfers.

**Apple's Typography Strategy**: Apple uses San Francisco as its system font -- chosen not for style but for *legibility at every size*. Headlines are bold and confident (semibold, large tracking). Body text is never cramped. Line heights are generous. Typography is the #1 vehicle for hierarchy.

**Apple's Whitespace Strategy**: Apple uses whitespace not as decoration but as *architecture*. Empty space is structural -- it creates rhythm, separates concerns, and guides the eye. Apple's product pages use 40-60% whitespace. This communicates confidence: "We don't need to fill every pixel to prove our value."

**Apple's Color Strategy**: Restrained palette. One or two accent colors maximum. Neutral backgrounds. Color is used *functionally* -- to draw attention, to signal state (success/error/warning), never decoratively.

### Dieter Rams' 10 Principles Applied to Ova

| # | Principle | Application to Ova |
|---|-----------|-------------------|
| 1 | **Innovative** | Innovation in fintech = making complex flows (FX, bridge, KYC) feel simple, not adding flashy features |
| 2 | **Useful** | Every screen must answer: "What can I DO here?" Remove anything that doesn't serve the user's immediate goal |
| 3 | **Aesthetic** | Visual quality reflects institutional trustworthiness. A bank that looks cheap feels unsafe |
| 4 | **Understandable** | Self-explanatory interfaces. No instruction needed. Transfer status should be immediately parseable |
| 5 | **Unobtrusive** | The interface is a tool, not a destination. Users come to move money, not admire the UI |
| 6 | **Honest** | Never mislead about fees, rates, timing, or capabilities. Transparency IS the design |
| 7 | **Long-lasting** | Avoid trendy design patterns. No glassmorphism, no brutalism, no neon gradients. Classic = trustworthy |
| 8 | **Thorough to the last detail** | Error states, loading states, empty states, edge cases -- ALL must be designed with care |
| 9 | **Environmentally conscious** | Minimize unnecessary renders, animations, page loads. Performance IS a design decision |
| 10 | **As little design as possible** | "Less, but better." Every element must justify its existence. When in doubt, remove |

### 2026 Fintech Design Principles (Industry)

From current fintech design research:

- **Quiet Confidence**: Replace visual flash with clarity. Users don't visit a banking site to be impressed -- they want to feel safe.
- **Calm Motion**: Subtle transitions that confirm actions without creating anxiety. No bouncy animations.
- **Micro-Confidence**: Small visual/textual affirmations that actions were received and processed correctly.
- **Visual Respiration**: Spacious layouts with cognitive breaks between decisions. One clear action per view.
- **Type as Reassurance**: Typography builds credibility through readability, not decoration. Min 16px body, 1.5 line height.

---

## Part 2: Current Codebase Audit

### Critical Design Problems Identified

#### Problem 1: Split Personality (Two Different Design Languages)

The codebase has **two completely different design systems** that don't cohere:

- **Landing page (`page.tsx`)**: Blue-teal gradient aesthetic with custom `ova-` CSS classes, navy/sky color palette, decorative grid backgrounds, floating blurred circles, phone mockups
- **Dashboard/Auth pages**: Black/white/gray minimalist system using Tailwind defaults

This is the #1 problem. A user goes from a "gradient-blue fintech startup" landing page to a "black-and-white SaaS tool" dashboard. The brand breaks.

#### Problem 2: Landing Page Looks Like SaaS, Not a Bank

The current landing page has these SaaS-startup hallmarks:
- Decorative grid background (`.ova-surface-grid`) -- looks like a developer tool
- Floating colored blur circles -- looks like Vercel/Linear
- "Chip" badges with uppercase tracking -- startup pattern
- Phone mockup with gradient shell -- mobile app landing pattern
- Feature cards in 3-column grid -- SaaS feature comparison pattern
- "Scroll story" interaction -- marketing gimmick, not banking

Banks don't do this. Banks communicate: stability, heritage, trustworthiness, simplicity.

#### Problem 3: Typography Lacks Hierarchy and Confidence

Current state:
- Logo font: "Avenir Next" with -0.08em tracking (decent)
- Display font: "Iowan Old Style" italic serif -- used only once in the hero
- Body font: Same "Avenir Next" everywhere
- Heading sizes jump inconsistently (text-4xl, text-3xl, text-2xl, text-xl)
- No clear typographic scale being followed despite one defined in tailwind.config.ts

Problem: The type system exists in config but isn't used consistently. The serif italic in the hero feels disconnected. Headlines don't feel authoritative enough.

#### Problem 4: Color Palette is Overcomplex and Uncommitted

Current CSS variables:
```
--ova-ink: #10243a
--ova-navy: #0f3558
--ova-sky: #1f82cd
--ova-teal: #56bfa8
--ova-canvas: #f5fbff
--ova-line: #d2e4f1
```

Plus Tailwind config colors:
```
ova-ink, ova-slate, ova-sky, ova-mint, ova-frost, ova-canvas, ova-glow
```

Plus dozens of hardcoded hex values in page.tsx (e.g., `#496985`, `#17324e`, `#8fb4d2`, `#9bbad4`, `#2a567b`, `#0f3659`...). I counted **40+ unique color values** across the landing page alone.

This is the opposite of systematic design. Each element was colored individually rather than pulling from a controlled palette.

#### Problem 5: Dashboard Components Are Functional But Not Premium

The dashboard pages (home, transfer, accounts, history, settings) use:
- `bg-black text-white` for accent elements (balance cards, sidebar)
- Generic gray scale (`gray-50` through `gray-900`)
- Standard `rounded-lg`, `border-gray-200` patterns
- No elevation system
- No consistent spacing rhythm
- Unicode characters as icons (◉, ↗, ▤, ☰, ⚙) -- reads as placeholder/prototype

These pages are *functional* but look like a developer's prototype, not a shipped banking product.

#### Problem 6: No Loading/Empty/Error State Design System

Loading states: plain text "Loading..." in gray
Empty states: plain text "No X yet" in gray
Error states: red-50 background with red-200 border (standard Tailwind pattern)

These deserve as much design care as the happy path. Banks need to feel reassuring even when things are loading or empty.

#### Problem 7: Lack of Motion Design Language

Current animations:
- `ova-reveal`: fade + translateY(22px) -- landing page only
- `ova-pulse-line`: opacity pulse on progress bars
- `ova-scene-in`: phone scene transition

Dashboard has NO motion at all. No page transitions, no micro-interactions, no hover feedback beyond color changes.

---

## Part 3: Redesign Proposals

### Proposal 1: Unified Color System -- "Quiet Navy"

Replace the sprawling color palette with a disciplined system:

```
Primary:
  --ova-black: #0A0A0A        (near-black for text, primary actions)
  --ova-charcoal: #1A1A1A     (sidebar, cards on dark)
  --ova-graphite: #2D2D2D     (secondary text on dark)

Neutral:
  --ova-900: #171717           (primary text)
  --ova-700: #404040           (secondary text)
  --ova-500: #737373           (tertiary/placeholder)
  --ova-300: #D4D4D4           (borders)
  --ova-100: #F5F5F5           (subtle backgrounds)
  --ova-50:  #FAFAFA           (page background)
  --ova-white: #FFFFFF         (cards, inputs)

Accent (used sparingly):
  --ova-blue: #1A6FD4          (links, active states)
  --ova-green: #16803C         (success, positive amounts)
  --ova-amber: #B45309         (warnings)
  --ova-red: #DC2626           (errors, destructive)
```

**Rationale**: This palette is essentially grayscale with functional color. It communicates seriousness. Blue is used only for interactive elements. Green only for money-positive signals. No teal. No gradients. No decorative color.

### Proposal 2: Typography System -- "Confident Clarity"

```css
/* Primary font: Inter or SF Pro Display (system) */
font-family: 'Inter', -apple-system, 'SF Pro Display', 'Segoe UI', sans-serif;

/* Scale (Major Third - 1.25 ratio) */
--text-display:  3rem    / 1.0  / -0.025em  / semibold   /* 48px - hero */
--text-h1:       2.25rem / 1.15 / -0.02em   / semibold   /* 36px - page titles */
--text-h2:       1.75rem / 1.2  / -0.015em  / semibold   /* 28px - section titles */
--text-h3:       1.25rem / 1.3  / -0.01em   / medium     /* 20px - card headers */
--text-body-lg:  1.0625rem / 1.6 / 0        / regular    /* 17px - important body */
--text-body:     0.9375rem / 1.6 / 0        / regular    /* 15px - default body */
--text-body-sm:  0.8125rem / 1.5 / 0        / regular    /* 13px - secondary */
--text-caption:  0.6875rem / 1.4 / 0.02em   / medium     /* 11px - labels, badges */
--text-mono:     0.8125rem / 1.5 / 0        / regular    /* 13px - IDs, hashes */
```

**Key changes**:
- Replace Avenir Next with Inter (open source, designed for screens, excellent number rendering)
- Remove the serif display font entirely -- banks don't mix serif/sans randomly
- Every text element must map to one of these tokens, no ad-hoc sizing
- Generous line heights for body text (1.5-1.6) for readability
- Tight line heights for headings (1.0-1.2) for impact

### Proposal 3: Spacing & Layout System -- "Breathe"

```css
/* 4px base unit, 8px grid */
--space-1:  0.25rem   /* 4px  - minimum gap */
--space-2:  0.5rem    /* 8px  - tight spacing */
--space-3:  0.75rem   /* 12px - related elements */
--space-4:  1rem      /* 16px - standard gap */
--space-6:  1.5rem    /* 24px - section padding */
--space-8:  2rem      /* 32px - card padding */
--space-12: 3rem      /* 48px - section separation */
--space-16: 4rem      /* 64px - major sections */
--space-24: 6rem      /* 96px - page sections */
```

**Key layout rules**:
- Max content width: 720px for forms, 960px for dashboards, 1200px for landing
- Card padding: Always 24px (space-6) or 32px (space-8)
- Section margins: Always 64px+ (space-16 or space-24)
- Form elements: 12px gap between label and input, 20px between fields
- Never use padding < 16px on interactive elements

### Proposal 4: Landing Page Redesign -- "Less, But Better"

**Remove entirely**:
- Grid background pattern (SaaS cliche)
- Floating blur circles (startup cliche)
- Phone mockup and "scroll story" section (marketing gimmick)
- Capability cards grid (SaaS feature comparison)
- Chip badges
- "next-generation money operations" tagline (buzzwordy)

**Replace with**:

```
[Nav: ova logo left, "Sign in" right, nothing else]

[Hero: full width, 40vh minimum]
  "Banking that moves at the speed of your business"
  or
  "Cross-border transfers between Turkey and Europe. Instant. Transparent. Secure."

  [One paragraph of substance, max 2 sentences]

  [One CTA: "Open an account" -- full width on mobile, auto on desktop]
  [Secondary: "Already have an account? Sign in"]

[Trust strip: Licensed | BDDK Regulated | KVKK Compliant | PSD2 Authorized]

[Three value propositions, stacked vertically, not in cards]:
  "TRY <-> EUR in seconds"
  Brief explanation. One sentence.

  "Real-time FX rates with transparent fees"
  Brief explanation. One sentence.

  "Bank-grade security on every transfer"
  Brief explanation. One sentence.

[CTA section: centered, minimal]
  "Ready to move money without borders?"
  [Open account button]

[Footer: ova, legal links, regulatory disclosures]
```

**Why this works**:
- No distracting visual elements
- The product's value is communicated through words, not animations
- Trust signals appear early (licensing, regulation)
- Single CTA focuses user attention
- Vertical stacking respects reading flow
- Massive whitespace communicates confidence

### Proposal 5: Dashboard Redesign -- "Instrument Panel"

**Sidebar**:
- Keep dark sidebar but refine: `#111111` background, not pure black
- Replace Unicode icons with a proper icon system (Lucide, Phosphor, or custom SVG)
- Active state: left accent bar (3px, ova-blue) + white text, not bg-white/10
- Logo: smaller, more refined. Consider wordmark only
- Remove "v0.1" version tag -- communicates "prototype"

**Home/Dashboard**:
- Balance cards: Full-width stack (one per currency), not grid
  - Each card: large balance number left, currency + status right
  - Background: subtle gradient (white to ova-50), not solid black
  - Balance should be THE largest number on screen (text-display size)
- Quick actions: Icon buttons in a horizontal strip, not standard text buttons
- Recent activity: Denser list with amount, recipient, date. No card wrapper needed
- Add: "Good morning, [name]" greeting with date (Apple-like personal touch)

**Transfer page**:
- Tab bar: Underline tabs are fine, refine to 2px active indicator
- Form: Increase input heights to 48px minimum (touch-friendly, premium feel)
- Amount input: Large display-size number as user types (like Apple Pay)
- FX quote card: White background, clear visual hierarchy
  - Exchange rate: prominent
  - Countdown: subtle, not alarming (avoid red until < 5s)
  - Confirm button: green or blue, not black (positive action = positive color)
- Success state: Checkmark animation (not static), clear "what happens next"

### Proposal 6: Auth Pages -- "Confident Simplicity"

**Current**: Functional but bare. White page, centered form, "Ova" in bold.

**Proposed refinements**:
- Split layout on desktop: Left half = subtle brand imagery or value prop, Right half = form
- Or: Keep centered but add more vertical breathing room
- Logo: Use lowercase "ova" consistently (matching landing page)
- Input fields: Increase to 48px height, 16px border-radius
- Submit button: Full-width, 48px height, slight shadow on hover
- Social proof: "Trusted by X,000+ businesses in Turkey and Europe" below form
- Error messages: Inline per-field (not banner at top)

### Proposal 7: Component System Refinements

**Buttons**:
```
Primary: bg-ova-900, text-white, rounded-xl, h-12, px-6
  Hover: bg-ova-black, subtle shadow
  Active: scale(0.98) for 100ms

Secondary: bg-white, border-ova-300, text-ova-900, rounded-xl, h-12, px-6
  Hover: bg-ova-50, border-ova-400

Ghost: transparent, text-ova-700
  Hover: bg-ova-100
```

**Cards**:
```
Default: bg-white, border-ova-200, rounded-2xl, p-6, shadow-sm
  Hover: shadow-md (if interactive)
  No translateY on hover -- too playful for banking
```

**Inputs**:
```
Default: bg-white, border-ova-300, rounded-xl, h-12, px-4
  Focus: border-ova-blue, ring-2 ring-ova-blue/20 (not ring-black)
  Error: border-ova-red, ring-2 ring-ova-red/20
  Labels: text-ova-700, text-body-sm, font-medium
```

**Status pills**:
```
Success: bg-green-50, text-green-700, font-medium
Warning: bg-amber-50, text-amber-700, font-medium
Error: bg-red-50, text-red-700, font-medium
Info: bg-blue-50, text-blue-700, font-medium
Neutral: bg-ova-100, text-ova-700, font-medium
```

### Proposal 8: Motion Design -- "Barely There"

```css
/* Standard transitions */
--transition-fast: 150ms ease
--transition-base: 200ms ease
--transition-slow: 300ms ease

/* Use for: hover states, focus rings, color changes */
transition: all var(--transition-fast);

/* Page transitions: subtle fade only */
@keyframes page-enter {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* Loading: single pulse dot or thin progress bar, never bouncing dots */
/* No reveal animations on scroll -- content should just BE there */
/* No parallax, no floating elements, no decorative motion */
```

**Motion rules**:
1. Never animate content into view on scroll (removes "scroll story" entirely)
2. Transitions should be < 200ms for interactive elements
3. Loading states: thin indeterminate progress bar at top of viewport
4. Success confirmations: subtle scale(1.02) + opacity change, < 300ms
5. Everything respects `prefers-reduced-motion`

---

## Part 4: Design System Specification Summary

### Do's
- Use whitespace as a primary design element
- Let typography carry hierarchy (size, weight, color -- in that order)
- Keep interactive elements large (min 44px touch targets)
- Use functional color only (green = money/success, red = error, blue = interactive)
- Design every state (loading, empty, error, success)
- Use consistent, predictable patterns across every page

### Don'ts
- Don't use gradients for decoration
- Don't use background patterns or textures
- Don't use floating/blurred decorative elements
- Don't use more than 2-3 font weights on a single page
- Don't use animations longer than 300ms
- Don't use parallax or scroll-driven animations
- Don't use colored backgrounds on cards (white only)
- Don't mix serif and sans-serif
- Don't use uppercase text for anything except labels/badges
- Don't use emoji or Unicode characters as icons

---

## Part 5: Page-by-Page Priority

| Page | Priority | Impact | Effort |
|------|----------|--------|--------|
| Landing page | P1 | Highest -- first impression | High |
| Login/Signup | P2 | Trust gate -- where users commit | Medium |
| Dashboard home | P3 | Daily touchpoint | Medium |
| Transfer flow | P4 | Core revenue action | High |
| KYC onboarding | P5 | Conversion critical | Medium |
| Accounts/History/Settings | P6 | Supporting pages | Low |

---

*This document is a working draft. Awaiting competitor analysis and feedback from competitor-analyst before finalizing into `docs/frontend-redesign-plan.md`.*
