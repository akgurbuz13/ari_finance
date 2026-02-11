# Frontend Redesign Implementation Plan

> **Status**: Implementation-Ready (v4 — post-screenshot review)
> **Date**: 2026-02-10
> **Branch**: `frontend-changes`
> **Based on**: Deep codebase audit + design principles + competitor analysis (`docs/competitor-analysis/analysis.md`)
> **Design Identity**: "Quiet Confidence, Institutional Precision"

---

## Overview

This plan upgrades the Ova web frontend from its current solid-but-prototype state into a production-grade fintech interface competing with Revolut, Wise, PayPal, and Papara. The current codebase already has a good brand identity foundation (navy palette, Inter font, custom Tailwind tokens, Lucide icons). This plan focuses on **elevating** the existing work with richer content, better micro-interactions, improved information density, responsive design, and polished empty/loading/error states.

### Competitor Analysis Alignment

The competitor analysis (`docs/competitor-analysis/analysis.md`) confirmed our foundation is already aligned with industry leaders:

| What Ova Already Has Right | Competitor Validation |
|---|---|
| Inter font | Used by Wise — "industry standard for financial UI, excellent number rendering" |
| Lucide SVG icons | ALL competitors use SVG icon systems; none use Unicode characters |
| Navy palette with restrained color count | Revolut uses ~4 colors, Wise ~8, PayPal ~6 — Ova's token system is well-scoped |
| Clean white backgrounds | ALL competitors use white/neutral backgrounds — no grids, blurs, or gradients |
| FxCalculator on landing | Wise's calculator-as-hero is "the gold standard" — Ova already has this |

Key gaps identified by competitor analysis that this plan addresses:
1. **No trust signals above the fold** — ALL competitors show regulatory info prominently (Revolut: "70M+ customers", Wise: Trustpilot, PayPal: "FDIC insured", Papara: "23M+ users" + Central Bank supervision). Note: PayPal exited Turkey in 2016, so Ova's real TR→EU competitors are Revolut, Wise, and Papara only.
2. **No sticky navigation** — ALL competitors use sticky headers with glass/shadow on scroll
3. **No button press animation** — ALL competitors use `scale(0.97-0.98)` for 100-150ms tactile feedback
4. **Auth pages not wrapped in cards** — ALL competitors use clean centered single-column forms
5. **No page transition animations** — All competitors use subtle fade/slide, NOT heavy parallax
6. **Limited social proof** — Competitors prominently display customer counts, volume, trust metrics

**What's already good** (preserve):
- Ova brand color system (navy, blue, neutral grays) in Tailwind config
- Inter font with well-defined type scale (display through caption)
- Component library (Button with 5 variants, Card, Input, StatusPill, Skeleton)
- AuthGuard + middleware auth pattern
- Cross-border transfer 4-step flow (form → quote → progress → success)
- FX quote countdown with color-coded urgency
- API client with JWT interceptors and token refresh

**What needs improvement** (this plan):
- Landing page lacks depth — needs "How it works", features grid, stats, richer footer
- Auth pages need Card wrappers, animated transitions, password strength
- Dashboard home needs amounts in transaction list, better quick actions, onboarding
- Transfer page needs enhanced FX quote display, better form validation
- Remaining pages (accounts, history, settings, KYC) need visual polish
- No page transition animations anywhere
- No responsive/mobile support
- Empty/error states are functional but not premium

**Scope**: ~25 files modified, 2-3 new files created, 1 new dependency (framer-motion)

---

## Current Architecture (from codebase audit)

### File Structure
```
web/
├── app/
│   ├── page.tsx                          # Landing page (180 lines)
│   ├── layout.tsx                        # Root layout with Inter font
│   ├── globals.css                       # CSS variables + Tailwind layers
│   ├── (auth)/
│   │   ├── login/page.tsx                # Login with 2FA support (118 lines)
│   │   ├── signup/page.tsx               # Signup with region selector (120 lines)
│   │   ├── forgot-password/page.tsx      # Forgot password (100 lines)
│   │   └── reset-password/page.tsx       # Reset with Suspense (182 lines)
│   ├── (dashboard)/
│   │   ├── layout.tsx                    # AuthGuard + Sidebar + Header (21 lines)
│   │   ├── home/page.tsx                 # Dashboard home (237 lines)
│   │   ├── transfer/page.tsx             # Transfer with domestic/cross-border (597 lines)
│   │   ├── accounts/page.tsx             # Account list with expand (228 lines)
│   │   ├── history/page.tsx              # Transaction history (193 lines)
│   │   └── settings/page.tsx             # Tabbed settings (283 lines)
│   └── (onboarding)/
│       └── kyc/page.tsx                  # KYC stepper (179 lines)
├── components/
│   ├── layout/
│   │   ├── AuthGuard.tsx                 # Client-side auth guard (49 lines)
│   │   ├── Header.tsx                    # Top bar with logout (42 lines)
│   │   └── Sidebar.tsx                   # Navy sidebar with Lucide icons (76 lines)
│   └── ui/
│       ├── Button.tsx                    # 5 variants, 3 sizes, forwardRef (96 lines)
│       ├── Card.tsx                      # Default + BalanceCard (55 lines)
│       ├── FxCalculator.tsx              # Landing page calculator (116 lines)
│       ├── Input.tsx                     # With error state, forwardRef (41 lines)
│       ├── Skeleton.tsx                  # Pulse + SkeletonCard (38 lines)
│       ├── StatusPill.tsx                # 5 variants (31 lines)
│       └── TransferProgress.tsx          # Timeline stepper (126 lines)
├── lib/
│   ├── api/
│   │   ├── client.ts                     # Axios with JWT interceptors (57 lines)
│   │   └── types.ts                      # TypeScript interfaces (85 lines)
│   └── hooks/
│       └── useAuth.ts                    # Auth hook with login/signup/logout (67 lines)
├── middleware.ts                          # Route matching middleware (37 lines)
├── tailwind.config.ts                    # Ova design tokens (99 lines)
├── next.config.js                        # Rewrites, typed routes (17 lines)
└── package.json                          # Next 14.2.5, React 18, Lucide, Axios, clsx
```

### Current Dependencies
```json
{
  "axios": "^1.7.2",
  "clsx": "^2.1.1",
  "lucide-react": "^0.563.0",
  "next": "14.2.5",
  "react": "18.3.1",
  "react-dom": "18.3.1"
}
```

### Current Design Tokens (tailwind.config.ts)
- Colors: `ova.navy`, `ova.navy-light`, `ova.blue`, `ova.blue-hover`, `ova.blue-light`, neutral scale (950-50, white), functional (green, red, amber + light variants)
- Font sizes: display (48px), h1 (36px), h2 (28px), h3 (20px), body-lg (17px), body (15px), body-sm (13px), caption (11px)
- Max widths: form (720px), dashboard (960px), landing (1200px)
- Border radius: xl (1rem), 2xl (1.5rem)
- Shadows: card, card-hover, sm
- Transitions: fast (150ms), base (200ms), slow (300ms)
- Animation: hero-enter (fade only)

---

## New Dependency

```bash
cd web && npm install framer-motion@^11
```

**framer-motion** (~40KB gzipped, tree-shakeable): For page transitions, animated presence (2FA field reveal, error banners, success states), and landing page scroll animations. Industry standard for React animation.

---

## Phase 1: Design System Foundation

**Goal**: Extend the Tailwind config and global CSS with additional tokens for animations, gradients, and elevation. No visible changes yet — just foundational tokens.

**Competitor insight**: All competitors use consistent, restrained design tokens. Revolut achieves premium feel with essentially 2 colors (black + white + one accent). Wise documents their full design system publicly at wise.design. PayPal uses `clamp()`-based fluid spacing. Our foundation is solid — this phase adds the motion and elevation tokens we're missing.

**Files**: `tailwind.config.ts`, `globals.css`, `layout.tsx`

### 1.1 `web/tailwind.config.ts`

Add the following to the `extend` object:

```typescript
// Add inside theme.extend:

backgroundImage: {
  'gradient-card': 'linear-gradient(135deg, #0D1B2A 0%, #1B2D3E 100%)',
  'gradient-subtle': 'linear-gradient(180deg, #FFFFFF 0%, #FAFAFA 100%)',
},

// Replace the existing animation/keyframes with expanded set:
animation: {
  'hero-enter': 'heroEnter 600ms ease-out',
  'fade-in': 'fadeIn 300ms ease-out',
  'fade-in-up': 'fadeInUp 400ms ease-out',
  'slide-in-right': 'slideInRight 300ms ease-out',
  'scale-in': 'scaleIn 200ms ease-out',
  'shimmer': 'shimmer 2s linear infinite',
  'float': 'float 6s ease-in-out infinite',
  'spin-slow': 'spin 3s linear infinite',
},
keyframes: {
  heroEnter: {
    '0%': { opacity: '0', transform: 'translateY(12px)' },
    '100%': { opacity: '1', transform: 'translateY(0)' },
  },
  fadeIn: {
    '0%': { opacity: '0' },
    '100%': { opacity: '1' },
  },
  fadeInUp: {
    '0%': { opacity: '0', transform: 'translateY(8px)' },
    '100%': { opacity: '1', transform: 'translateY(0)' },
  },
  slideInRight: {
    '0%': { opacity: '0', transform: 'translateX(-8px)' },
    '100%': { opacity: '1', transform: 'translateX(0)' },
  },
  scaleIn: {
    '0%': { opacity: '0', transform: 'scale(0.95)' },
    '100%': { opacity: '1', transform: 'scale(1)' },
  },
  shimmer: {
    '0%': { backgroundPosition: '-200% 0' },
    '100%': { backgroundPosition: '200% 0' },
  },
  float: {
    '0%, 100%': { transform: 'translateY(0)' },
    '50%': { transform: 'translateY(-6px)' },
  },
},
```

### 1.2 `web/app/globals.css`

Add to the `:root` block:
```css
/* Elevation */
--shadow-xs: 0 1px 2px rgba(0,0,0,0.04);
--shadow-sm: 0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
--shadow-md: 0 4px 6px -1px rgba(0,0,0,0.07), 0 2px 4px -1px rgba(0,0,0,0.04);
--shadow-lg: 0 10px 15px -3px rgba(0,0,0,0.08), 0 4px 6px -2px rgba(0,0,0,0.04);
/* Glass */
--glass-bg: rgba(255,255,255,0.85);
--glass-border: rgba(229,229,229,0.5);
```

Add to `@layer components`:
```css
/* Shimmer skeleton */
.skeleton-shimmer {
  background: linear-gradient(90deg, var(--ova-200) 25%, var(--ova-100) 50%, var(--ova-200) 75%);
  background-size: 200% 100%;
  animation: shimmer 2s linear infinite;
}

/* Large monetary display */
.amount-hero {
  font-size: 3rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.025em;
  line-height: 1;
}
```

### 1.3 `web/app/layout.tsx`

Add Open Graph metadata:
```typescript
export const metadata: Metadata = {
  title: "Ova - Cross-border transfers between Turkey and Europe",
  description: "Move money between TRY and EUR with live rates, transparent fees, and real-time settlement tracking.",
  keywords: ["banking", "fintech", "Turkey", "Europe", "TRY", "EUR", "cross-border", "transfers"],
  openGraph: {
    title: 'Ova - Cross-border transfers between Turkey and Europe',
    description: 'Move money between TRY and EUR with live rates, transparent fees, and real-time settlement tracking.',
    type: 'website',
  },
};
```

### Phase 1 Acceptance Criteria
- [ ] `npm run build` passes with zero errors
- [ ] New animation classes available in Tailwind
- [ ] CSS variables accessible in browser dev tools
- [ ] No visual regressions on any existing page

---

## Phase 2: Landing Page Overhaul

**Goal**: Enrich the landing page with additional sections while preserving its existing solid structure: navigation, hero with FxCalculator, trust bar, value propositions, CTA, footer.

**Competitor patterns adopted**:
- **Sticky glass navigation**: ALL competitors (Revolut, Wise, PayPal, Papara) use sticky headers. Revolut adds slight shadow + backdrop-blur on scroll.
- **Calculator-as-hero**: Wise's live currency calculator in the hero is "unique among competitors and immediately demonstrates value" — Ova already has FxCalculator here, we're polishing it.
- **Trust signals above the fold**: Revolut shows "35M+ customers", Wise shows Trustpilot ratings, PayPal shows "FDIC insured", Papara shows Central Bank supervision. Ova needs to show "BDDK Regulated | KVKK Compliant | PSD2 Authorized" prominently.
- **Animated number counters**: PayPal uses scroll-triggered counting animations for social proof stats.
- **Multi-column footer**: Revolut and Wise both use comprehensive 4-column footers with regulatory disclosures at the bottom.
- **Subtle scroll animations**: Revolut uses "subtle fade-in on scroll, NOT heavy parallax". All competitors prioritize fast, content-focused experiences.

**Files**: `web/app/page.tsx`, `web/components/ui/FxCalculator.tsx`, new `web/components/ui/AnimatedCounter.tsx`

### 2.1 `web/app/page.tsx` — Enhance (not rewrite)

The current landing page already has the right structure. Enhance it with:

**A. Navigation — Make sticky with glass effect on scroll:**

Add `'use client'` directive and scroll state:
```tsx
'use client';
import { useState, useEffect } from 'react';

export default function LandingPage() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 10);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);
```

Update nav element:
```tsx
<nav className={clsx(
  "fixed top-0 left-0 right-0 z-50 transition-all duration-base",
  scrolled
    ? "bg-[var(--glass-bg)] backdrop-blur-sm border-b border-[var(--glass-border)] shadow-xs"
    : "bg-white border-b border-ova-200"
)}>
```

Add center navigation links (Personal, Business, Fees — link to anchor sections on the same page):
```tsx
<div className="hidden md:flex items-center gap-6">
  <a href="#features" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">Features</a>
  <a href="#how-it-works" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">How it works</a>
  <a href="#fees" className="text-body-sm font-medium text-ova-700 hover:text-ova-900 transition-colors duration-fast">Fees</a>
</div>
```

Add mobile hamburger menu:
```tsx
const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
// ... hamburger button on right side (visible md:hidden)
// ... slide-down menu panel with links
```

**B. Hero — Add staggered animations with framer-motion:**

Import framer-motion:
```tsx
import { motion } from 'framer-motion';
```

Wrap hero text elements with staggered animation:
```tsx
<motion.h1
  initial={{ opacity: 0, y: 12 }}
  animate={{ opacity: 1, y: 0 }}
  transition={{ duration: 0.5, delay: 0.1 }}
  className="text-display text-ova-navy text-balance"
>
  Cross-border transfers between Turkey and Europe.
</motion.h1>

<motion.p
  initial={{ opacity: 0, y: 12 }}
  animate={{ opacity: 1, y: 0 }}
  transition={{ duration: 0.5, delay: 0.2 }}
  className="mt-2 text-h2 text-ova-navy font-semibold"
>
  Instant. Transparent. Secure.
</motion.p>
```

Apply similar stagger to body text (delay 0.3), CTA buttons (delay 0.4).

Add subtle float animation to FxCalculator wrapper:
```tsx
<motion.div
  initial={{ opacity: 0, y: 16 }}
  animate={{ opacity: 1, y: 0 }}
  transition={{ duration: 0.6, delay: 0.3 }}
>
  <FxCalculator />
</motion.div>
```

**C. Add "How it works" section (NEW — between trust bar and value props):**

```tsx
{/* ──── HOW IT WORKS ──── */}
<section id="how-it-works" className="py-20 bg-white">
  <div className="mx-auto max-w-landing px-6">
    <h2 className="text-h1 text-ova-navy text-center">How Ova works</h2>
    <p className="mt-3 text-body-lg text-ova-500 text-center max-w-md mx-auto">
      Three steps to move money between Turkey and Europe
    </p>

    <div className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-8">
      {/* Step 1 */}
      <div className="text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-100">
          <UserPlus size={28} strokeWidth={1.5} className="text-ova-navy" />
        </div>
        <h3 className="mt-5 text-h3 text-ova-900">Create your account</h3>
        <p className="mt-2 text-body-sm text-ova-500">
          Sign up in under 2 minutes with your email and phone number.
        </p>
      </div>

      {/* Step 2 */}
      <div className="text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-100">
          <ShieldCheck size={28} strokeWidth={1.5} className="text-ova-navy" />
        </div>
        <h3 className="mt-5 text-h3 text-ova-900">Verify your identity</h3>
        <p className="mt-2 text-body-sm text-ova-500">
          Quick KYC verification unlocks transfers up to EUR 50,000.
        </p>
      </div>

      {/* Step 3 */}
      <div className="text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-ova-100">
          <Zap size={28} strokeWidth={1.5} className="text-ova-navy" />
        </div>
        <h3 className="mt-5 text-h3 text-ova-900">Send money instantly</h3>
        <p className="mt-2 text-body-sm text-ova-500">
          Live FX rates, transparent fees, and real-time settlement tracking.
        </p>
      </div>
    </div>
  </div>
</section>
```

Import `UserPlus`, `ShieldCheck`, `Zap` from `lucide-react`.

**D. Enhance value propositions into Features section (convert existing):**

Convert the existing vertical list into a 2x2 grid with icons (id="features"):

```tsx
{/* ──── FEATURES ──── */}
<section id="features" className="py-20">
  <div className="mx-auto max-w-landing px-6">
    <h2 className="text-h1 text-ova-navy text-center">Why choose Ova</h2>
    <div className="mt-12 grid grid-cols-1 md:grid-cols-2 gap-6">
      <FeatureCard
        icon={<TrendingUp size={24} strokeWidth={1.5} />}
        title="Real-time FX rates"
        description="Live market rates with no hidden markups. Rate locks for 30 seconds so you always know what you'll pay."
      />
      <FeatureCard
        icon={<Receipt size={24} strokeWidth={1.5} />}
        title="Transparent fees"
        description="0.25% flat fee on every transfer. No surprises, no tiers, no fine print."
      />
      <FeatureCard
        icon={<Zap size={24} strokeWidth={1.5} />}
        title="Instant settlement"
        description="Cross-border transfers settle in under 2 minutes with real-time tracking at every step."
      />
      <FeatureCard
        icon={<Lock size={24} strokeWidth={1.5} />}
        title="Bank-grade security"
        description="Two-factor authentication, KYC verification, sanctions screening, and end-to-end encryption."
      />
    </div>
  </div>
</section>
```

New inline helper component:
```tsx
function FeatureCard({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) {
  return (
    <div className="bg-white border border-ova-200 rounded-2xl p-8 shadow-card hover:shadow-card-hover transition-shadow duration-base">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-ova-100 text-ova-navy">
        {icon}
      </div>
      <h3 className="mt-5 text-h3 text-ova-900">{title}</h3>
      <p className="mt-2 text-body-sm text-ova-500 leading-relaxed">{description}</p>
    </div>
  );
}
```

Import `TrendingUp`, `Receipt`, `Lock` from `lucide-react`.

**E. Add FX Rate Banner (NEW section — navy background for visual rhythm):**

```tsx
{/* ──── FX RATE BANNER ──── */}
<section id="fees" className="py-16 bg-ova-navy">
  <div className="mx-auto max-w-landing px-6 text-center">
    <p className="text-caption text-white/60 uppercase tracking-wide">Live exchange rate</p>
    <p className="mt-3 text-display text-white amount">
      1 EUR = ₺{(1 / 0.0271).toFixed(2)} TRY
    </p>
    <p className="mt-2 text-body-sm text-white/50">
      Updated continuously · 0.25% flat fee on all transfers
    </p>
    <Link
      href="/signup"
      className="mt-6 inline-flex h-12 items-center rounded-xl bg-white px-6 text-body-sm font-medium text-ova-navy transition-all duration-base hover:bg-ova-50 hover:shadow-sm active:scale-[0.98]"
    >
      Start sending money
    </Link>
  </div>
</section>
```

**F. Add Stats Section (NEW — social proof with animated counters):**

```tsx
{/* ──── STATS ──── */}
<section className="py-20 bg-ova-100">
  <div className="mx-auto max-w-landing px-6">
    <div className="grid grid-cols-1 md:grid-cols-3 gap-8 text-center">
      <div>
        <p className="text-display text-ova-navy amount">
          <AnimatedCounter target={50000} suffix="+" />
        </p>
        <p className="mt-2 text-body-sm text-ova-500">Transfers completed</p>
      </div>
      <div>
        <p className="text-display text-ova-navy amount">
          €<AnimatedCounter target={100} suffix="M+" />
        </p>
        <p className="mt-2 text-body-sm text-ova-500">Volume processed</p>
      </div>
      <div>
        <p className="text-display text-ova-navy amount">
          <AnimatedCounter target={2} suffix=" min" />
        </p>
        <p className="mt-2 text-body-sm text-ova-500">Average delivery time</p>
      </div>
    </div>
  </div>
</section>
```

**G. Enhance Footer (convert to 4-column layout):**

Replace existing footer with:
```tsx
<footer className="border-t border-ova-200 py-12">
  <div className="mx-auto max-w-landing px-6">
    <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
      {/* Brand column */}
      <div className="col-span-2 md:col-span-1">
        <p className="ova-logo text-2xl">ova</p>
        <p className="mt-2 text-body-sm text-ova-500">
          Designed for trust.<br />Built for movement.
        </p>
      </div>

      {/* Product */}
      <div>
        <h4 className="text-caption text-ova-400 uppercase tracking-wide">Product</h4>
        <ul className="mt-3 space-y-2">
          <li><a href="#features" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Transfers</a></li>
          <li><a href="#fees" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Fees & Rates</a></li>
          <li><a href="#how-it-works" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">How it works</a></li>
        </ul>
      </div>

      {/* Company */}
      <div>
        <h4 className="text-caption text-ova-400 uppercase tracking-wide">Company</h4>
        <ul className="mt-3 space-y-2">
          <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">About</a></li>
          <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Security</a></li>
          <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Contact</a></li>
        </ul>
      </div>

      {/* Legal */}
      <div>
        <h4 className="text-caption text-ova-400 uppercase tracking-wide">Legal</h4>
        <ul className="mt-3 space-y-2">
          <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Terms</a></li>
          <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Privacy</a></li>
          <li><a href="#" className="text-body-sm text-ova-500 hover:text-ova-700 transition-colors duration-fast">Compliance</a></li>
        </ul>
      </div>
    </div>

    <div className="mt-10 border-t border-ova-200 pt-6">
      <p className="text-caption text-ova-400">
        Ova Financial Services, licensed by BDDK (Turkey) and authorized under PSD2 (European Union).
        KVKK and GDPR compliant. All transfers are subject to regulatory compliance checks.
      </p>
      <p className="mt-2 text-caption text-ova-400">
        © {new Date().getFullYear()} Ova Financial Services. All rights reserved.
      </p>
    </div>
  </div>
</footer>
```

### 2.2 `web/components/ui/FxCalculator.tsx` — Polish

1. Add flag emojis to currency labels:
   - Change `sendCurrency` display to include flag: `{'\u{1F1F9}\u{1F1F7}'} TRY` or `{'\u{1F1EA}\u{1F1FA}'} EUR`

2. Add "Live rate" indicator with green dot:
   ```tsx
   <div className="flex items-center gap-1.5">
     <span className="h-2 w-2 rounded-full bg-ova-green animate-pulse" />
     <span className="text-caption text-ova-green font-medium">Live rate</span>
   </div>
   ```

3. Rotate swap button on click:
   ```tsx
   const [swapRotation, setSwapRotation] = useState(0);
   const handleSwap = () => {
     setSwapRotation(prev => prev + 180);
     setDirection(d => d === 'TRY_EUR' ? 'EUR_TRY' : 'TRY_EUR');
   };
   // Apply: style={{ transform: `rotate(${swapRotation}deg)`, transition: 'transform 300ms ease-out' }}
   ```

4. Add subtle card hover shadow:
   ```tsx
   className="... hover:shadow-card-hover transition-shadow duration-base"
   ```

### 2.3 New: `web/components/ui/AnimatedCounter.tsx`

```tsx
'use client';

import { useEffect, useRef, useState } from 'react';

interface AnimatedCounterProps {
  target: number;
  prefix?: string;
  suffix?: string;
  duration?: number;
}

export default function AnimatedCounter({
  target,
  prefix = '',
  suffix = '',
  duration = 2000,
}: AnimatedCounterProps) {
  const [count, setCount] = useState(0);
  const ref = useRef<HTMLSpanElement>(null);
  const hasAnimated = useRef(false);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !hasAnimated.current) {
          hasAnimated.current = true;
          const start = Date.now();
          const tick = () => {
            const elapsed = Date.now() - start;
            const progress = Math.min(elapsed / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            setCount(Math.floor(eased * target));
            if (progress < 1) requestAnimationFrame(tick);
          };
          requestAnimationFrame(tick);
        }
      },
      { threshold: 0.5 }
    );
    if (ref.current) observer.observe(ref.current);
    return () => observer.disconnect();
  }, [target, duration]);

  return (
    <span ref={ref}>
      {prefix}{count.toLocaleString()}{suffix}
    </span>
  );
}
```

### Phase 2 Acceptance Criteria
- [ ] Navigation sticks on scroll with glass effect
- [ ] Hero text animates in with stagger
- [ ] Mobile hamburger menu works
- [ ] "How it works" 3-step section visible
- [ ] Features 2x2 grid section visible with icons
- [ ] FX Rate Banner with navy background visible
- [ ] Stats section animates numbers on scroll
- [ ] Footer has 4-column layout with legal text
- [ ] FxCalculator swap button rotates
- [ ] FxCalculator shows "Live rate" green dot
- [ ] All links work (/login, /signup, anchor scrolls)
- [ ] Page fully responsive (375px, 768px, 1280px)

---

## Phase 3: Auth Pages Redesign

**Goal**: Wrap forms in Cards, add animated transitions, password strength indicators, and stronger trust signals.

**Competitor patterns adopted**:
- **Centered single-column form**: ALL competitors use this — "None use decorative split layouts — the auth page is purely functional" (competitor analysis).
- **Progressive authentication**: Revolut, Wise, and PayPal all use email-first → password flow. Our current approach (both fields at once) is acceptable for now but consider progressive auth as a future enhancement.
- **Clean Card wrapper**: All competitors frame their auth forms in elevated white cards with subtle shadow, matching our Card component pattern.
- **Revolut's SSO insight**: Revolut uses `sso.revolut.com` for domain isolation (prevents XSS on marketing site from compromising auth). Not implementing subdomain SSO now, but our auth pages should feel equally clean and professional.
- **Passkey consideration**: Revolut is "one of the first fintechs to implement FIDO2 passkeys for web login" — noted as future enhancement, not in this plan.

**Files**: 4 auth pages + 1 new component

### 3.1 `web/app/(auth)/login/page.tsx`

1. **Wrap form in a Card shell** for visual elevation:
   ```tsx
   <div className="bg-white border border-ova-200 rounded-2xl shadow-card p-8 sm:p-10">
     {/* existing form content moves inside here */}
   </div>
   ```

2. **Animate 2FA field reveal** with framer-motion:
   ```tsx
   import { motion, AnimatePresence } from 'framer-motion';

   <AnimatePresence>
     {needs2FA && (
       <motion.div
         initial={{ height: 0, opacity: 0 }}
         animate={{ height: 'auto', opacity: 1 }}
         exit={{ height: 0, opacity: 0 }}
         transition={{ duration: 0.2 }}
       >
         <Input label="2FA Code" ... />
       </motion.div>
     )}
   </AnimatePresence>
   ```

3. **Animate error banner** entry:
   ```tsx
   <AnimatePresence>
     {error && (
       <motion.div
         initial={{ opacity: 0, y: -8 }}
         animate={{ opacity: 1, y: 0 }}
         exit={{ opacity: 0, y: -8 }}
         className="p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red"
       >
         {error}
       </motion.div>
     )}
   </AnimatePresence>
   ```

4. **Add divider** between form and footer links:
   ```tsx
   <div className="border-t border-ova-200 mt-6 pt-6">
     {/* existing links */}
   </div>
   ```

5. **Add trust signal below card** (competitor pattern — all competitors show trust cues on auth):
   ```tsx
   import { ShieldCheck } from 'lucide-react';
   // Below the Card wrapper:
   <p className="text-caption text-ova-400 text-center mt-6 flex items-center justify-center gap-1.5">
     <ShieldCheck size={14} strokeWidth={1.5} />
     BDDK regulated · Encrypted end-to-end
   </p>
   ```
   Apply this same trust line to ALL 4 auth pages.

### 3.2 `web/app/(auth)/signup/page.tsx`

1. **Wrap form in Card** (same pattern as login)

2. **Replace `<select>` region with styled radio buttons:**
   ```tsx
   <div>
     <label className="block text-body-sm font-medium text-ova-700 mb-3">Region</label>
     <div className="grid grid-cols-2 gap-3">
       <button
         type="button"
         onClick={() => setRegion('TR')}
         className={clsx(
           'flex items-center gap-3 p-4 rounded-xl border-2 transition-all duration-fast text-left',
           region === 'TR'
             ? 'border-ova-navy bg-ova-50'
             : 'border-ova-200 hover:border-ova-300'
         )}
       >
         <span className="text-2xl">{'\u{1F1F9}\u{1F1F7}'}</span>
         <div>
           <p className="text-body-sm font-medium text-ova-900">Turkey</p>
           <p className="text-caption text-ova-500">TRY accounts</p>
         </div>
       </button>
       <button
         type="button"
         onClick={() => setRegion('EU')}
         className={clsx(
           'flex items-center gap-3 p-4 rounded-xl border-2 transition-all duration-fast text-left',
           region === 'EU'
             ? 'border-ova-navy bg-ova-50'
             : 'border-ova-200 hover:border-ova-300'
         )}
       >
         <span className="text-2xl">{'\u{1F1EA}\u{1F1FA}'}</span>
         <div>
           <p className="text-body-sm font-medium text-ova-900">European Union</p>
           <p className="text-caption text-ova-500">EUR accounts</p>
         </div>
       </button>
     </div>
   </div>
   ```

3. **Add password strength indicator** below password field:
   ```tsx
   import PasswordStrength from '../../../components/ui/PasswordStrength';
   // Below the password Input:
   {password && <PasswordStrength password={password} />}
   ```

4. **Animate error banner** (same as login)

### 3.3 `web/app/(auth)/forgot-password/page.tsx`

1. **Wrap in Card**
2. **Animate success state transition** with AnimatePresence
3. **Add mail icon** to success state:
   ```tsx
   import { Mail } from 'lucide-react';
   // In success state:
   <div className="flex h-14 w-14 items-center justify-center rounded-full bg-ova-green-light mx-auto mb-4">
     <Mail size={28} strokeWidth={1.5} className="text-ova-green" />
   </div>
   ```

### 3.4 `web/app/(auth)/reset-password/page.tsx`

1. **Wrap in Card**
2. **Add PasswordStrength** below new password field
3. **Add password requirements help text**:
   ```tsx
   <p className="text-caption text-ova-400 mt-1">Must be at least 8 characters</p>
   ```
4. **Animate success checkmark** with scale-in

### 3.5 New: `web/components/ui/PasswordStrength.tsx`

```tsx
'use client';

import { clsx } from 'clsx';

function getStrength(password: string): { level: 0 | 1 | 2 | 3; label: string } {
  if (password.length < 8) return { level: 0, label: 'Too short' };
  let score = 0;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/\d/.test(password)) score++;
  if (/[^a-zA-Z0-9]/.test(password)) score++;
  if (password.length >= 12) score++;
  if (score <= 1) return { level: 1, label: 'Weak' };
  if (score <= 2) return { level: 2, label: 'Medium' };
  return { level: 3, label: 'Strong' };
}

const colors = ['bg-ova-red', 'bg-ova-red', 'bg-ova-amber', 'bg-ova-green'];
const textColors = ['text-ova-red', 'text-ova-red', 'text-ova-amber', 'text-ova-green'];

export default function PasswordStrength({ password }: { password: string }) {
  const { level, label } = getStrength(password);

  return (
    <div className="mt-2">
      <div className="flex gap-1">
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            className={clsx(
              'h-1 flex-1 rounded-full transition-colors duration-fast',
              i < level ? colors[level] : 'bg-ova-200'
            )}
          />
        ))}
      </div>
      <p className={clsx('text-caption mt-1', textColors[level])}>{label}</p>
    </div>
  );
}
```

### Phase 3 Acceptance Criteria
- [ ] All 4 auth pages wrapped in Card with shadow
- [ ] Login: 2FA field animates in/out smoothly
- [ ] Login: error banner animates in/out
- [ ] Signup: region selector uses styled radio buttons with flags
- [ ] Signup: password strength indicator appears below password field
- [ ] Forgot-password: success state has mail icon + animation
- [ ] Reset-password: has password strength indicator
- [ ] All auth flows still work correctly (login → /home, signup → /home, etc.)
- [ ] Trust signal (Shield icon + text) visible on all auth pages
- [ ] All pages responsive on mobile

---

## Phase 4: Dashboard Redesign

**Goal**: Upgrade the dashboard layout (sidebar, header) and home page with better visual hierarchy, micro-interactions, and information density.

**Competitor patterns adopted**:
- **Sticky navigation with page context**: All competitors keep navigation accessible while scrolling. Header should show current page title (like PayPal and Wise do).
- **User avatar in navigation**: Revolut and Wise show user identity in the nav with dropdown for settings/logout.
- **Card hover states**: Revolut uses "shadow elevation increase (shadow-sm to shadow-md)" on card hover with "no bounce or dramatic effects — everything is subtle and confident."
- **Page transitions**: All competitors use "smooth, fast" client-side transitions. Revolut specifically noted for fast page transitions via client-side routing. We're adding subtle opacity/y fade (150ms).
- **Large balance numbers**: Revolut and Wise both make account balances prominent as the primary visual element.

**Files**: `Sidebar.tsx`, `Header.tsx`, `(dashboard)/layout.tsx`, `home/page.tsx`, `Card.tsx`

### 4.1 `web/components/layout/Sidebar.tsx`

1. **Improve active state** with background highlight:
   ```tsx
   isActive
     ? "border-l-[3px] border-white text-white bg-white/10"
     : "border-l-[3px] border-transparent text-white/60 hover:text-white/85 hover:bg-white/5"
   ```

2. **Add user section at bottom** (import `useAuth`):
   ```tsx
   import { useAuth } from '../../lib/hooks/useAuth';

   // Inside component:
   const { user } = useAuth();
   const initials = user
     ? `${(user.firstName || user.email)?.[0] || ''}`.toUpperCase()
     : '?';
   const displayName = user?.firstName || user?.email?.split('@')[0] || '';

   // Before the bottom nav section:
   <div className="px-6 py-4 border-t border-white/10">
     <div className="flex items-center gap-3">
       <div className="flex h-8 w-8 items-center justify-center rounded-full bg-white/20 text-caption text-white font-medium">
         {initials}
       </div>
       <div className="flex-1 min-w-0">
         <p className="text-body-sm text-white truncate">{displayName}</p>
         <p className="text-caption text-white/40 truncate">{user?.email}</p>
       </div>
     </div>
   </div>
   ```

### 4.2 `web/components/layout/Header.tsx`

1. **Add page title on the left** based on pathname:
   ```tsx
   import { usePathname } from 'next/navigation';
   import { Bell } from 'lucide-react';

   const pathname = usePathname();
   const pageTitle: Record<string, string> = {
     '/home': 'Dashboard',
     '/transfer': 'Transfer',
     '/accounts': 'Accounts',
     '/history': 'History',
     '/settings': 'Settings',
   };
   const title = pageTitle[pathname || ''] || 'Dashboard';

   // In JSX, left side of header:
   <span className="text-h3 text-ova-900">{title}</span>
   ```

2. **Add notification bell** (visual placeholder):
   ```tsx
   <button className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-ova-100 transition-colors duration-fast">
     <Bell size={18} strokeWidth={1.5} className="text-ova-500" />
   </button>
   ```

3. **Replace text "Logout" with avatar dropdown**:
   ```tsx
   const [dropdownOpen, setDropdownOpen] = useState(false);

   <div className="relative">
     <button
       onClick={() => setDropdownOpen(!dropdownOpen)}
       className="flex h-9 w-9 items-center justify-center rounded-full bg-ova-navy text-caption font-medium text-white"
     >
       {initials}
     </button>
     {dropdownOpen && (
       <div className="absolute right-0 top-full mt-2 w-48 bg-white border border-ova-200 rounded-xl shadow-md py-1 z-50 animate-scale-in">
         <Link href="/settings" className="block px-4 py-2.5 text-body-sm text-ova-700 hover:bg-ova-50">Settings</Link>
         <button onClick={handleLogout} className="block w-full text-left px-4 py-2.5 text-body-sm text-ova-red hover:bg-ova-red-light">Logout</button>
       </div>
     )}
   </div>
   ```

   Add click-outside handler:
   ```tsx
   useEffect(() => {
     const handleClickOutside = (e: MouseEvent) => {
       if (dropdownOpen) setDropdownOpen(false);
     };
     document.addEventListener('click', handleClickOutside);
     return () => document.removeEventListener('click', handleClickOutside);
   }, [dropdownOpen]);
   ```

### 4.3 `web/app/(dashboard)/layout.tsx`

Add page transition animation:
```tsx
'use client';

import { usePathname } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import Sidebar from '../../components/layout/Sidebar';
import Header from '../../components/layout/Header';
import AuthGuard from '../../components/layout/AuthGuard';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <AuthGuard>
      <div className="min-h-screen bg-ova-50">
        <Sidebar />
        <Header />
        <main className="ml-60 pt-16 p-8">
          <motion.div
            key={pathname}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
          >
            {children}
          </motion.div>
        </main>
      </div>
    </AuthGuard>
  );
}
```

### 4.4 `web/app/(dashboard)/home/page.tsx`

1. **Add amounts to recent activity list** — currently only shows type + status. Add amount display:
   ```tsx
   // In the transaction row, add between the type info and StatusPill:
   <span className="text-body-sm font-medium text-ova-900 amount">
     {tx.type === 'deposit' || tx.type === 'mint' ? '+' : '-'}
     {/* Amount would come from tx.metadata or a separate field */}
   </span>
   ```
   Note: The `Transaction` type doesn't have an `amount` field. Use `tx.metadata` if available, or just improve the visual layout.

2. **Enhance balance cards** — make numbers larger:
   ```tsx
   // In BalanceCard usage, the component already uses amount-display class
   // Enhance by adding flag emoji:
   <BalanceCard
     key={account.id}
     currency={`${account.currency === 'TRY' ? '\u{1F1F9}\u{1F1F7}' : '\u{1F1EA}\u{1F1FA}'} ${account.currency}`}
     amount={formatCurrency(account.balance, account.currency)}
   />
   ```

3. **Enhance quick actions** — larger icons:
   ```tsx
   // Change icon circles from h-10 w-10 to h-12 w-12
   <div className="flex h-12 w-12 items-center justify-center rounded-full bg-ova-navy text-white">
   ```

4. **Upgrade empty state for new users**:
   ```tsx
   {accounts.length === 0 && (
     <Card className="col-span-2 bg-gradient-card text-white">
       <div className="text-center py-8">
         <h2 className="text-h2 text-white">Welcome to Ova</h2>
         <p className="text-body text-white/70 mt-2">
           Create your first account to start sending money between Turkey and Europe.
         </p>
         <div className="mt-6 flex justify-center gap-3">
           <Button variant="secondary" onClick={() => router.push('/accounts')}>
             Create TRY Account
           </Button>
           <Button variant="secondary" onClick={() => router.push('/accounts')}>
             Create EUR Account
           </Button>
         </div>
       </div>
     </Card>
   )}
   ```

5. **Make Account Health section actionable**:
   ```tsx
   <Card>
     <h3 className="text-body-sm font-medium text-ova-700 mb-4">Account Health</h3>
     <div className="flex flex-wrap gap-4">
       {user?.status !== 'active' ? (
         <Link href="/kyc" className="flex items-center gap-2 p-3 rounded-xl bg-ova-amber-light hover:bg-ova-amber-light/80 transition-colors duration-fast">
           <StatusPill variant="warning">KYC Pending</StatusPill>
           <span className="text-caption text-ova-amber">Complete verification →</span>
         </Link>
       ) : (
         <StatusPill variant="success">KYC Verified</StatusPill>
       )}
       {!user?.totpEnabled ? (
         <Link href="/settings" className="flex items-center gap-2 p-3 rounded-xl bg-ova-amber-light hover:bg-ova-amber-light/80 transition-colors duration-fast">
           <StatusPill variant="warning">2FA Disabled</StatusPill>
           <span className="text-caption text-ova-amber">Enable 2FA →</span>
         </Link>
       ) : (
         <StatusPill variant="success">2FA Enabled</StatusPill>
       )}
     </div>
   </Card>
   ```

### 4.5 `web/components/ui/Button.tsx` — Press feedback + pill shape

**Competitor insight**: ALL competitors (Revolut, Wise, PayPal, Papara) use `scale(0.97-0.98)` on button press for tactile feedback with ~150ms transition. ALL also use pill-shaped primary CTAs (border-radius: 1000px or ~24px). Screenshots confirm: Revolut (pill), Wise (pill), PayPal (border-radius: 1000px).

Two changes:

1. **Move `active:scale-[0.98]` to the base button class** so ALL variants get press feedback (currently only `primary` has it):
```tsx
// In the base className (line 79), add:
"active:scale-[0.98]"
// Remove `active:scale-[0.98]` from the primary variant string
```

2. **Change primary button border-radius to pill shape**:
```tsx
// Change base button from "rounded-xl" to conditional:
variant === 'primary' ? 'rounded-full' : 'rounded-xl',
// This gives primary buttons the pill shape ALL competitors use
// while keeping secondary/ghost/danger with rounded-xl
```

These two changes bring our button system fully in line with ALL competitor patterns.

### 4.6 `web/components/ui/Card.tsx`

Add hover prop for interactive cards:
```tsx
interface CardProps {
  children: ReactNode;
  header?: string;
  className?: string;
  padding?: "standard" | "generous";
  hover?: boolean;
}

// In the className:
hover && "hover:shadow-card-hover cursor-pointer transition-shadow duration-fast",
```

### Phase 4 Acceptance Criteria
- [ ] Sidebar active items have `bg-white/10` background
- [ ] Sidebar shows user avatar + name + email at bottom
- [ ] Header shows page title on the left side
- [ ] Header has notification bell icon
- [ ] Header avatar opens dropdown with Settings + Logout
- [ ] Dashboard pages fade in on navigation
- [ ] Balance cards show flag emoji with currency
- [ ] Quick action icons are larger (h-12 w-12)
- [ ] New users see welcome banner with gradient background
- [ ] Account Health items link to /kyc and /settings when action needed
- [ ] All API calls still work correctly

---

## Phase 5: Transfer Flow & Remaining Pages

**Goal**: Polish the transfer page (core revenue flow), accounts, history, settings, and KYC pages.

**Competitor patterns adopted**:
- **Fee transparency**: Wise's entire brand is built on transparent fee display — "fee comparison showing Wise vs. bank fees" is a primary trust mechanism. Our FX quote display should make the total cost unmistakable.
- **Filter chips**: Revolut uses type-based filter chips in transaction history for quick filtering.
- **Interactive transaction rows**: Click-to-expand for transaction details follows the progressive disclosure pattern used by Wise and Revolut.
- **Regulatory trust in KYC**: Papara prominently shows "Central Bank of Turkey supervision" — our KYC approved state should similarly communicate what regulatory-backed features are now unlocked.
- **Button transitions**: ALL competitors use ~200ms ease transitions with `scale(0.97-0.98)` press feedback. PayPal specifically documents "200ms ease for color, background, border, box-shadow" transitions.

**Files**: `transfer/page.tsx`, `accounts/page.tsx`, `history/page.tsx`, `settings/page.tsx`, `kyc/page.tsx`, `TransferProgress.tsx`

### 5.1 `web/app/(dashboard)/transfer/page.tsx`

1. **Add icons to tabs**:
   ```tsx
   import { ArrowUpRight, Globe } from 'lucide-react';
   // Domestic tab: <ArrowUpRight size={16} className="mr-1.5" /> Domestic
   // Cross-border tab: <Globe size={16} className="mr-1.5" /> Cross-Border
   ```

2. **Improve domestic form** — add flag+balance to account selector:
   ```tsx
   <option key={a.id} value={a.id}>
     {a.currency === 'TRY' ? '🇹🇷' : '🇪🇺'} {a.currency} — {formatAmount(a.balance, a.currency)}
   </option>
   ```

3. **Add total cost summary to FX quote**:
   ```tsx
   // After the existing quote details, add:
   <div className="flex justify-between text-body-sm font-medium border-t border-ova-200 pt-3 mt-3">
     <span className="text-ova-700">Total cost</span>
     <span className="text-ova-900">
       {formatAmount(
         parseFloat(cbQuote.sourceAmount) * (1 + parseFloat(cbQuote.spread) / 100),
         cbQuote.sourceCurrency
       )}
     </span>
   </div>
   ```

4. **Improve success checkmark animation**:
   ```tsx
   import { motion } from 'framer-motion';

   // Replace static checkmark circle with:
   <motion.div
     initial={{ scale: 0, opacity: 0 }}
     animate={{ scale: 1, opacity: 1 }}
     transition={{ type: 'spring', stiffness: 200, damping: 15, delay: 0.1 }}
     className="w-16 h-16 bg-ova-green-light rounded-full flex items-center justify-center mx-auto"
   >
     <CheckCircle2 size={32} strokeWidth={1.5} className="text-ova-green" />
   </motion.div>
   ```

5. **Add inline form validation** for amount field:
   ```tsx
   // Below the amount input:
   {cbAmount && sourceAccount && parseFloat(cbAmount) > parseFloat(sourceAccount.balance) && (
     <p className="mt-1 text-caption text-ova-red">Insufficient balance</p>
   )}
   ```

### 5.2 `web/app/(dashboard)/accounts/page.tsx`

1. **Add summary header** above account list:
   ```tsx
   {accounts.length > 0 && (
     <div className="flex items-baseline justify-between">
       <div>
         <h1 className="text-h2 text-ova-900">Accounts</h1>
         <p className="text-body-sm text-ova-500 mt-1">{accounts.length} account{accounts.length !== 1 ? 's' : ''}</p>
       </div>
       <div className="flex gap-2">
         {/* existing create buttons */}
       </div>
     </div>
   )}
   ```

2. **Add "View transactions" link** to each AccountCard:
   ```tsx
   // At the bottom of AccountCard, after the expandable details:
   <Link
     href="/history"
     className="inline-flex items-center gap-1 text-caption text-ova-blue hover:underline mt-3"
   >
     View transactions <ArrowRight size={12} />
   </Link>
   ```

3. **Improve empty state** with larger visual:
   ```tsx
   <Card>
     <div className="text-center py-12 space-y-4">
       <div className="flex h-16 w-16 items-center justify-center rounded-full bg-ova-100 mx-auto">
         <Wallet size={32} strokeWidth={1.5} className="text-ova-400" />
       </div>
       <h2 className="text-h3 text-ova-900">No accounts yet</h2>
       <p className="text-body-sm text-ova-400 max-w-sm mx-auto">
         Create a TRY or EUR account to start sending and receiving money between Turkey and Europe.
       </p>
       <div className="flex justify-center gap-3 pt-2">
         <Button variant="secondary" onClick={() => createAccount('TRY')}>
           {'\u{1F1F9}\u{1F1F7}'} Create TRY Account
         </Button>
         <Button variant="secondary" onClick={() => createAccount('EUR')}>
           {'\u{1F1EA}\u{1F1FA}'} Create EUR Account
         </Button>
       </div>
     </div>
   </Card>
   ```

### 5.3 `web/app/(dashboard)/history/page.tsx`

1. **Add type filter** alongside account selector:
   ```tsx
   const [typeFilter, setTypeFilter] = useState('all');

   // Add filter row:
   <div className="flex flex-wrap gap-2">
     {['all', 'p2p_transfer', 'cross_border', 'fx_conversion', 'deposit', 'fee'].map((type) => (
       <button
         key={type}
         onClick={() => setTypeFilter(type)}
         className={clsx(
           'px-3 py-1.5 rounded-full text-caption font-medium transition-colors duration-fast',
           typeFilter === type
             ? 'bg-ova-navy text-white'
             : 'bg-ova-100 text-ova-500 hover:bg-ova-200'
         )}
       >
         {type === 'all' ? 'All' : type.replace(/_/g, ' ')}
       </button>
     ))}
   </div>
   ```

   Filter the transactions:
   ```tsx
   const filteredTransactions = typeFilter === 'all'
     ? transactions
     : transactions.filter(tx => tx.type === typeFilter);
   ```

2. **Add click-to-expand** for transaction details:
   ```tsx
   const [expandedTx, setExpandedTx] = useState<string | null>(null);

   // In transaction row:
   <div
     key={tx.id}
     className="cursor-pointer hover:bg-ova-50 transition-colors duration-fast -mx-6 px-6"
     onClick={() => setExpandedTx(expandedTx === tx.id ? null : tx.id)}
   >
     {/* existing row content */}
     {expandedTx === tx.id && (
       <div className="mt-2 pb-2 pl-11 space-y-1">
         <p className="text-caption text-ova-400">
           Transaction ID: <span className="font-mono text-ova-500">{tx.id}</span>
         </p>
         {tx.referenceId && (
           <p className="text-caption text-ova-400">
             Reference: <span className="font-mono text-ova-500">{tx.referenceId}</span>
           </p>
         )}
         <p className="text-caption text-ova-400">
           Date: {new Date(tx.createdAt).toLocaleString('en-GB')}
         </p>
       </div>
     )}
   </div>
   ```

### 5.4 `web/app/(dashboard)/settings/page.tsx`

1. **Add password change form** to Security tab (currently just a disabled button):
   ```tsx
   // Replace the disabled "Change Password" button with:
   const [changingPassword, setChangingPassword] = useState(false);
   const [currentPassword, setCurrentPassword] = useState('');
   const [newPassword, setNewPassword] = useState('');
   const [passwordMessage, setPasswordMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

   // Show form when changingPassword is true:
   {changingPassword ? (
     <form onSubmit={handlePasswordChange} className="mt-4 space-y-4">
       <Input label="Current Password" type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} required />
       <Input label="New Password" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} required />
       {newPassword && <PasswordStrength password={newPassword} />}
       <div className="flex gap-2">
         <Button type="submit">Update Password</Button>
         <Button variant="ghost" type="button" onClick={() => setChangingPassword(false)}>Cancel</Button>
       </div>
     </form>
   ) : (
     <Button variant="secondary" onClick={() => setChangingPassword(true)}>Change Password</Button>
   )}
   ```

2. **Improve 2FA setup** — add instruction copy:
   ```tsx
   // Before the CopyableSecret components:
   <ol className="list-decimal list-inside space-y-2 text-body-sm text-ova-700">
     <li>Open your authenticator app (Google Authenticator, Authy, etc.)</li>
     <li>Copy the secret key below and add it as a new account</li>
     <li>Enter the 6-digit code from the app on your next login</li>
   </ol>
   ```

### 5.5 `web/app/(onboarding)/kyc/page.tsx`

1. **Improve stepper indicators** — larger:
   ```tsx
   // Change step circles from h-8 w-8 to h-10 w-10
   className="flex h-10 w-10 items-center justify-center rounded-full ..."
   ```

2. **Add step descriptions** below labels:
   ```tsx
   const steps = [
     { name: 'Identity', description: 'Personal information' },
     { name: 'Documents', description: 'ID verification' },
     { name: 'Review', description: 'Final check' },
   ];
   ```

3. **Improve approved state** — add feature unlock list with icons:
   ```tsx
   // Already has check items — enhance with Lucide icons:
   import { Check, Globe, RefreshCw, Headphones } from 'lucide-react';

   <ul className="text-body-sm text-ova-700 space-y-3 text-left inline-block">
     <li className="flex items-center gap-3">
       <div className="flex h-6 w-6 items-center justify-center rounded-full bg-ova-green-light">
         <Globe size={14} className="text-ova-green" />
       </div>
       International transfers up to EUR 50,000
     </li>
     <li className="flex items-center gap-3">
       <div className="flex h-6 w-6 items-center justify-center rounded-full bg-ova-green-light">
         <RefreshCw size={14} className="text-ova-green" />
       </div>
       Full FX conversion access
     </li>
     <li className="flex items-center gap-3">
       <div className="flex h-6 w-6 items-center justify-center rounded-full bg-ova-green-light">
         <Headphones size={14} className="text-ova-green" />
       </div>
       Priority support
     </li>
   </ul>
   ```

### 5.6 `web/components/ui/TransferProgress.tsx`

1. **Increase indicator size** from h-6 w-6 to h-8 w-8:
   ```tsx
   // In StepIndicator:
   <div className="flex h-8 w-8 items-center justify-center rounded-full ..."
   ```

2. **Add ring pulse on active step**:
   ```tsx
   if (status === 'active') {
     return (
       <div className="relative">
         <div className="absolute inset-0 rounded-full bg-ova-blue/20 animate-ping" style={{ animationDuration: '2s' }} />
         <div className="relative flex h-8 w-8 items-center justify-center rounded-full bg-ova-blue">
           <div className="h-2.5 w-2.5 rounded-full bg-white" />
         </div>
       </div>
     );
   }
   ```

3. **Update connecting line min-height**:
   ```tsx
   'w-0.5 flex-1 min-h-[28px]'  // was 24px
   ```

### Phase 5 Acceptance Criteria
- [ ] Transfer tabs have icons
- [ ] Transfer account selectors show flags
- [ ] FX quote has total cost summary
- [ ] Transfer success has animated checkmark (spring animation)
- [ ] Transfer has insufficient balance validation
- [ ] Accounts has summary header with account count
- [ ] Accounts empty state has larger visual + dual CTAs
- [ ] History has type filter chips
- [ ] History transactions expand on click
- [ ] Settings Security tab has password change form
- [ ] Settings 2FA setup has numbered instructions
- [ ] KYC stepper has larger indicators + descriptions
- [ ] KYC approved state has icon list
- [ ] TransferProgress has larger indicators with ring pulse
- [ ] All API calls continue to work correctly

---

## Phase 6: Polish & Responsive

**Goal**: Responsive design, consistent empty/loading/error states, accessibility, and final consistency pass across all files.

**Competitor patterns adopted**:
- **Mobile-first responsive**: Competitor analysis confirms "ALL competitors design for mobile first, desktop second." Wise documents breakpoints at 390px (mobile), 768px (tablet), 1440px (desktop).
- **Hamburger → full overlay on mobile**: Revolut uses "Hamburger menu with full-screen overlay" on mobile — we'll use a slide-out sidebar overlay.
- **Accessible focus states**: PayPal has "excellent accessibility with visible focus rings" — `0.1875rem solid #097ff5 with shadow offset 0.375rem`. Our `focus:ring-2 focus:ring-ova-blue` achieves similar results.
- **Shimmer loading states**: All competitors have "designed" loading states — not just text placeholders. Upgrading from `animate-pulse` to shimmer gradient.
- **Functional empty/error states**: Competitor analysis notes these should be "designed with the same care as happy-path screens."

**Files**: All ~25 files (touch pass)

### 6.1 Responsive Design

**Dashboard layout** — mobile sidebar becomes overlay:
```tsx
// In (dashboard)/layout.tsx:
const [sidebarOpen, setSidebarOpen] = useState(false);

<>
  {/* Mobile overlay */}
  {sidebarOpen && (
    <div className="fixed inset-0 bg-black/50 z-40 lg:hidden" onClick={() => setSidebarOpen(false)} />
  )}

  {/* Sidebar */}
  <div className={clsx(
    "fixed left-0 top-0 h-screen w-60 bg-ova-navy z-50 transition-transform duration-slow lg:translate-x-0",
    sidebarOpen ? "translate-x-0" : "-translate-x-full"
  )}>
    <Sidebar onClose={() => setSidebarOpen(false)} />
  </div>

  {/* Main */}
  <main className="lg:ml-60 pt-16 p-4 sm:p-6 lg:p-8">
    {children}
  </main>
</>
```

**Header** — add hamburger on mobile:
```tsx
<button className="lg:hidden" onClick={() => setSidebarOpen(true)}>
  <Menu size={20} strokeWidth={1.5} className="text-ova-500" />
</button>
```

Note: `setSidebarOpen` needs to be passed from layout to header, or use a simple context/callback.

**Landing page** — already responsive (grid-cols-1 on mobile). Verify:
- Hero: stacks to single column
- How it works: stacks to single column
- Features: stacks to single column
- Footer: 2-column on tablet, 1-column on mobile

### 6.2 Skeleton Enhancement

Replace `animate-pulse` with `skeleton-shimmer` class in `Skeleton.tsx`:
```tsx
className={clsx(
  "skeleton-shimmer",  // was "animate-pulse bg-ova-200"
  variant === "text" && "h-4 rounded",
  // ...rest
)}
```

### 6.3 Error State Enhancement

Add animated error banners to all dashboard pages that make API calls. Pattern:
```tsx
import { AnimatePresence, motion } from 'framer-motion';

<AnimatePresence>
  {error && (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      className="p-3 bg-ova-red-light border border-ova-red/20 rounded-xl text-body-sm text-ova-red"
    >
      {error}
    </motion.div>
  )}
</AnimatePresence>
```

### 6.4 Accessibility Pass

1. Verify all icon-only buttons have `aria-label`
2. Verify all form fields have associated labels
3. Add `role="alert"` to error/success banners
4. Verify focus order makes sense on all forms
5. Verify color contrast (already using established token system — should be fine)
6. Verify `prefers-reduced-motion` media query in globals.css disables all animations

### 6.5 Consistency Pass

Verify across ALL pages:
- [ ] Page titles: `text-h2 text-ova-900`
- [ ] Card usage: `<Card>` component, never raw divs
- [ ] Button usage: `<Button>` component, never raw `<button>`
- [ ] Input usage: `<Input>` component, never raw `<input>` (except special cases like FX amount)
- [ ] Status badges: `<StatusPill>` component
- [ ] Loading states: `<Skeleton>` component with shimmer
- [ ] Error banners: consistent red-light bg, red border, red text, rounded-xl
- [ ] Success banners: consistent green-light bg, green border, green text, rounded-xl
- [ ] Transition durations: `duration-fast` for hover, `duration-base` for state changes
- [ ] Focus rings: `focus:ring-2 focus:ring-ova-blue focus:ring-offset-2`
- [ ] Border radius: `rounded-xl` for containers/inputs/buttons, `rounded-2xl` for cards, `rounded-full` for pills/avatars

### Phase 6 Acceptance Criteria
- [ ] Dashboard works on mobile (375px): sidebar as overlay, content full-width
- [ ] Dashboard works on tablet (768px): appropriate spacing
- [ ] Landing page fully responsive
- [ ] All skeletons use shimmer animation
- [ ] All error banners animate in/out
- [ ] All interactive elements have focus rings
- [ ] All icon buttons have aria-label
- [ ] prefers-reduced-motion disables all animations
- [ ] Component usage is consistent across all pages
- [ ] `npm run build` passes with zero errors and no TS errors

---

## Files Modified/Created Summary

| Phase | Files Modified | Files Created |
|-------|---------------|---------------|
| 1 | `tailwind.config.ts`, `globals.css`, `layout.tsx` | — |
| 2 | `page.tsx`, `FxCalculator.tsx` | `AnimatedCounter.tsx` |
| 3 | `login/page.tsx`, `signup/page.tsx`, `forgot-password/page.tsx`, `reset-password/page.tsx` | `PasswordStrength.tsx` |
| 4 | `Sidebar.tsx`, `Header.tsx`, `(dashboard)/layout.tsx`, `home/page.tsx`, `Card.tsx` | — |
| 5 | `transfer/page.tsx`, `accounts/page.tsx`, `history/page.tsx`, `settings/page.tsx`, `kyc/page.tsx`, `TransferProgress.tsx` | — |
| 6 | All files (consistency pass), `Skeleton.tsx` | — |

**Total**: ~25 files modified, 2 new files created, 1 new dependency (framer-motion)

---

## Implementation Dependencies

```
Phase 1 ──────┬──── Phase 2 (Landing)
              ├──── Phase 3 (Auth) ─── can run parallel with Phase 2
              └──── Phase 4 (Dashboard) ─── can start after Phase 1
                      └──── Phase 5 (Remaining Pages)
                              └──── Phase 6 (Polish)
```

**Parallel opportunities**:
- Phases 2 and 3 are independent and can be done simultaneously
- Phase 5 pages (transfer, accounts, history, settings, kyc) are independent of each other
- Phase 6 should be last as it touches everything

---

## Risk Mitigation

1. **No API changes**: All `api.get()`, `api.post()`, `api.patch()` calls remain identical. Request/response shapes unchanged.
2. **No route changes**: All routes (`/`, `/login`, `/signup`, `/home`, `/transfer`, etc.) remain identical.
3. **No auth changes**: AuthGuard, middleware, useAuth hook, token management all unchanged.
4. **No type changes**: All TypeScript interfaces in `lib/api/types.ts` remain unchanged.
5. **Additive Tailwind changes**: Phase 1 only adds new tokens — doesn't modify existing ones.
6. **Per-phase commits**: Each phase is independently committable and deployable.

---

## Design References

### Competitor Patterns Adopted

Full competitor analysis at `docs/competitor-analysis/analysis.md`.

| Pattern | Source | Evidence | Application |
|---------|--------|----------|-------------|
| Sticky glass navigation | Revolut, Wise, PayPal, Papara | ALL competitors use sticky headers; Revolut adds backdrop-blur | Landing nav (Phase 2) |
| Calculator-as-hero | Wise | "Unique among competitors, immediately demonstrates value" | FxCalculator polish (Phase 2) |
| Trust signals above fold | ALL competitors | Revolut: "35M+ customers"; Wise: Trustpilot; PayPal: "FDIC insured"; Papara: Central Bank | Trust bar + Stats section (Phase 2) |
| Scroll-triggered counters | PayPal | "Animated number counters: scroll-triggered counting animations" | AnimatedCounter in Stats (Phase 2) |
| Multi-column footer | Revolut, Wise | "Multi-column with extensive links" + regulatory disclosures | 4-column footer (Phase 2) |
| Subtle fade-in animations | Revolut | "Subtle fade-in on scroll, NOT heavy parallax... fast and content-focused" | Hero stagger, page transitions (Phase 2, 4) |
| Centered single-column auth | ALL competitors | "None use decorative split layouts — the auth page is purely functional" | Card-wrapped auth forms (Phase 3) |
| Animated field reveals | Standard pattern | AnimatePresence for 2FA field, error banners | Auth page animations (Phase 3) |
| Large balance numbers | Revolut, Wise | "Headlines are the primary visual element" with tight tracking | Dashboard balance cards (Phase 4) |
| User identity in nav | Revolut, Wise | Avatar + dropdown for settings/logout in header | Header avatar dropdown (Phase 4) |
| Card hover elevation | Revolut | "Shadow elevation increase (shadow-sm to shadow-md), no bounce" | Card hover prop (Phase 4) |
| Page transitions | ALL competitors | Revolut: "fast page transitions via client-side routing" | Dashboard layout motion (Phase 4) |
| Fee transparency | Wise | "Fee breakdowns visible everywhere — trust through openness" | Total cost summary in FX quote (Phase 5) |
| Filter chips | Revolut | Transaction type filtering with chip UI | History type filter (Phase 5) |
| Progressive disclosure | Wise, Revolut | Click-to-expand transaction details | History expandable rows (Phase 5) |
| Button press feedback | ALL competitors | "scale(0.97-0.98) for tactile feedback, ~150-200ms ease" | active:scale-[0.98] on CTAs (Phase 5) |
| Mobile-first responsive | ALL competitors | Wise breakpoints: 390px, 768px, 1440px | Full responsive pass (Phase 6) |
| Hamburger menu overlay | Revolut | "Hamburger menu with full-screen overlay" on mobile | Dashboard mobile nav (Phase 6) |
| Shimmer loading states | ALL competitors | All have "designed" loading states, not text placeholders | Skeleton shimmer upgrade (Phase 6) |
| Accessible focus rings | PayPal | "Focus ring: 0.1875rem solid #097ff5 with shadow offset" | focus:ring-2 on all interactive elements (Phase 6) |

### Competitor Patterns Explicitly Avoided

Per competitor analysis "Design Patterns to Avoid":

| Pattern | Why |
|---------|-----|
| Decorative gradients | "No competitor uses gradient backgrounds for primary surfaces" |
| Grid/dot backgrounds | "SaaS pattern, not fintech. No competitor uses this" |
| Floating blur circles | "No competitor uses decorative blur elements. 2022-era startup aesthetic" |
| Heavy scroll animations | "No competitor uses dramatic scroll-triggered reveals" |
| Phone mockups as hero | "Never as the primary hero element with decorative shells" |
| Multiple competing CTAs | "All competitors have ONE clear primary CTA per section" |
| Uppercase body text | "Only used for very small labels/badges" |
| Mixed serif + sans-serif | "No competitor mixes serif + sans-serif" — Ova correctly uses Inter only |

### Future Enhancements (from competitor analysis, not in this plan)

| Enhancement | Source | Notes |
|-------------|--------|-------|
| SSO subdomain | Revolut (`sso.revolut.com`) | Domain isolation for auth security; requires infrastructure |
| FIDO2 passkeys | Revolut | First-mover advantage in web-based passkey auth |
| Mega-menu navigation | Revolut (20+ service pages) | Build nav component to support expansion as Ova adds services |
| Dark mode | Revolut, PayPal | Build color system with CSS custom properties for theme switching |
| Trustpilot integration | Wise | Show real customer reviews as social proof |
| QR code for mobile app | Wise, Papara | Landing page footer enhancement |
| One-input-per-screen onboarding | Industry trend (Appendix B) | "Multi-step wizards replacing long forms" — could improve KYC flow |
| Save & exit for incomplete sessions | Industry trend (Appendix B) | Useful for KYC — "Error states with immediate feedback reduce abandonment" |
| Dark-themed login option | Revolut (screenshot confirms) | Revolut uses navy/dark login; could be a premium auth variant |

### Design System Sources
- Apple Human Interface Guidelines (clarity, deference, depth)
- Dieter Rams' 10 Principles (less but better, honest, useful)
- Wise Design System (wise.design — publicly documented tokens, components, patterns)
- 2026 Fintech Design Trends (quiet confidence, calm motion, visual respiration)
