# ARI Competitor & Feature Analysis

> **Author**: Competitor Analysis Agent
> **Date**: 2026-02-09
> **Status**: Draft - Collaborating with design-principles agent

---

## Part 1: ARI MVP Feature Map

### Current Frontend Pages & Features

| Page | Route | Features | Current Design Quality |
|------|-------|----------|----------------------|
| Landing | `/` | Hero, value props, phone mockup, scroll story, capability cards, security section, footer | Polished but SaaS-looking, not banking |
| Login | `/login` | Email, password, 2FA code, error handling | Functional, minimal, white background |
| Signup | `/signup` | Email, phone, password, region selector (TR/EU) | Same as login, functional |
| Forgot Password | `/forgot-password` | Email input, success message | Basic, clean |
| Reset Password | `/reset-password` | Token-based, new password confirmation | Basic, clean |
| Dashboard Home | `/home` | Balance cards (TRY/EUR), quick actions, recent activity | Prototype quality, black cards |
| Transfer | `/transfer` | Domestic tab, cross-border tab with FX quote flow | Most complex page, functional |
| Accounts | `/accounts` | Account list with create buttons | Simple list view |
| History | `/history` | Transaction table with account selector | Standard table |
| Settings | `/settings` | Profile form, 2FA setup | Bare minimum |
| KYC | `/kyc` | Step indicator, status display, initiate verification | Basic stepper |

### Key Backend Capabilities That Frontend Must Surface

1. **Dual Currency** (TRY + EUR) - Two wallets, two sets of flows
2. **Cross-Border Transfers** with real-time FX quotes (30s TTL)
3. **ICTT Bridge** - Blockchain settlement status (mint/burn/relay)
4. **KYC Onboarding** - Identity verification with provider widget
5. **2FA/TOTP** - Security setup and enforcement
6. **Compliance Status** - Account freezing, sanctions, limits
7. **Transaction History** - Filterable by account, type, status
8. **Idempotent Payments** - Every transfer has unique key

### What Makes ARI Unique (Differentiators to Showcase)

1. **Turkey-EU corridor specialist** - Not a generic fintech, specific corridor
2. **Blockchain settlement** - On-chain transparency (not just marketing)
3. **Real compliance** - BDDK, MASAK, PSD2, KVKK - regulated in both markets
4. **Instant FX** - Live quotes with locked rates
5. **Dual-region architecture** - Data residency compliance built in

---

## Part 2: Competitor Deep Dive

### 1. Revolut

**What they do well:**
- **Dashboard**: Card-based wallet views with swipeable currency selector. Each currency is a "pocket" with its own transaction stream. The balance is always the hero element, massive typography.
- **Transfer flow**: Clean step-by-step with real-time conversion rate displayed prominently. Amount input is oversized - the number grows as you type.
- **Navigation**: Bottom tab bar on mobile, minimal sidebar on web. Never more than 4-5 primary destinations.
- **Onboarding**: 10-15 minutes from download to funded account. Progressive KYC - basic features unlock immediately, advanced features require enhanced verification.
- **Motion**: Smooth, fast transitions. Page changes feel instantaneous. Transaction confirmations use checkmark animations.
- **Color**: Clean white backgrounds, dark text, green for positive, red for negative. Accent blue is used sparingly for interactive elements.
- **Trust building**: Real-time transaction notifications, instant balance updates, clear fee breakdowns before confirmation.

**Applicable to ARI:**
- Currency pocket concept maps well to TRY/EUR wallets
- Progressive disclosure in transfer flows
- Balance-as-hero pattern
- Real-time feedback on all financial actions

### 2. Papara (Turkey Market)

**What they do well:**
- **Local relevance**: Turkish language first, TRY-centric, familiar payment patterns for Turkish users
- **Zero-fee messaging**: Prominently communicates no-fee structure
- **Simple interface**: Deliberately uncomplicated - younger Turkish demographic prefers simplicity
- **Bill payments integration**: Turkish utility bills, phone top-ups - everyday money tasks
- **QR payments**: Popular in Turkish market for in-person transfers

**Applicable to ARI:**
- Understanding the Turkish user's expectation for simplicity
- Fee transparency is critical in Turkey market
- Region-specific UX patterns (TR users expect different flows than EU users)
- Must feel familiar to someone who uses Papara daily

### 3. Midas (Turkey - Investment)

**What they do well:**
- **Data visualization**: Clean charts, real-time market data, portfolio breakdowns
- **Professional aesthetic**: Darker color scheme communicates seriousness
- **Performance focus**: Fast data loading, no decorative elements slowing the UI
- **Advanced features with simple UX**: Complex instruments (options, margin) presented clearly
- **Trust through transparency**: Free real-time data, competitive pricing shown upfront
- **Pro mode**: Power users get advanced analytics while basic view stays simple

**Applicable to ARI:**
- Financial data presentation patterns (balances, rates, history)
- Professional aesthetic that Turkish users associate with financial sophistication
- Tiered complexity (simple by default, detailed on demand)

### 4. Nubank (Brazil)

**What they do well:**
- **Brand color as identity**: Purple is Nubank. Single strong color creates instant recognition.
- **Design system (NuDS)**: 100+ reusable components, regional adaptations, comprehensive token system
- **Warmth without frivolity**: The interface feels friendly but not childish. "Warm minimalism."
- **Mobile-first perfection**: Every interaction feels native, not web-wrapped
- **Accessibility**: Design system built with accessibility first, not as afterthought
- **Trust through simplicity**: No hidden fees, clear statements, transparent communication
- **Emotional design**: Spending summaries, personalized insights feel human

**Applicable to ARI:**
- Single brand color strategy (could be navy/dark blue for ARI)
- Design system rigor - every component from a shared token system
- Warmth in financial interfaces - ARI doesn't need to be cold
- Regional adaptation patterns (TR vs EU variations)

### 5. N26 (Germany/EU)

**What they do well:**
- **Web app excellence**: One of few neobanks with a genuinely good web experience
- **Clean German design**: Precision, order, predictability. Every pixel justified.
- **Spaces feature**: Sub-accounts visualized clearly with progress bars and colors
- **Transaction categorization**: Automatic with visual icons
- **Security visibility**: Regulatory compliance displayed prominently. Cards show bank license numbers.
- **European compliance UX**: PSD2/SCA flows integrated smoothly

**Applicable to ARI:**
- Web-first design excellence (ARI is web-focused)
- European regulatory compliance display patterns
- Transaction categorization UI
- German design discipline (precision, systematic)

### 6. Wise (UK/Global)

**What they do well:**
- **FX transparency king**: The entire brand is built on showing the real exchange rate vs bank markup
- **Comparison pricing**: Shows what you'd pay with banks vs Wise
- **Transfer timeline**: Clear step-by-step progress of where your money is
- **Multi-currency balances**: Clean display of multiple currencies with conversion rates
- **Landing page**: Clean, benefit-focused, shows the product not marketing fluff
- **Trust through data**: Speed stats (80% instant, 88% within 24h) shown prominently
- **API/Platform**: Design system that works for consumers AND business integrations

**Applicable to ARI:**
- FX transparency patterns - critical for cross-border
- Transfer timeline/progress UI - maps perfectly to ICTT bridge status
- Multi-currency balance display
- Landing page that shows real product value, not SaaS marketing
- Speed/reliability stats as trust signals

### 7. Monzo (UK)

**What they do well:**
- **Hot coral brand**: Distinctive color makes the card and app instantly recognizable
- **Spending insights**: Category breakdowns, merchant recognition, budget tracking
- **Pots (savings)**: Visual progress towards savings goals
- **Community**: Open forum, transparent roadmap, users feel invested
- **Notification design**: Clear, actionable push notifications for every transaction
- **Playful-professional balance**: Fun without being unserious

**Applicable to ARI:**
- Transaction categorization and insights (post-MVP but good to design for)
- Savings visualization concepts
- Notification patterns for transfer status updates
- Balancing personality with professionalism

---

## Part 3: Cross-Cutting Design Patterns from Competitors

### Pattern 1: Balance Display
All successful fintechs make the balance the hero element:
- Revolut: Massive number, swipeable currencies
- Nubank: Large, centered, with available/total breakdown
- N26: Prominent with recent trend indicator
- **ARI should**: Display TRY and EUR balances as the primary visual element on dashboard, with display-size typography. Not in cards - just clean, large numbers.

### Pattern 2: Transfer Flows
Universally follow a wizard pattern:
1. Select recipient
2. Enter amount (large input)
3. Review details (fee breakdown, FX rate if applicable)
4. Confirm (with 2FA if required)
5. Success + tracking
- **ARI should**: Adopt this 5-step wizard for both domestic and cross-border. The FX quote step should be a clear "review and confirm" gate.

### Pattern 3: Trust Signals
Every serious fintech displays:
- Regulatory license numbers
- Security certifications
- Encryption indicators on sensitive pages
- "Your money is protected" messaging with specifics (FDIC, FSCS, BDDK equivalent)
- **ARI should**: Add BDDK license, PSD2 authorization, KVKK compliance badges in footer and on landing page. Show security indicators on transfer confirmation screens.

### Pattern 4: Landing Page Philosophy
Split between two schools:
- **Product-led** (Wise, Revolut): Show the actual product, real screenshots, real numbers
- **Brand-led** (Nubank, N26): Emotional messaging, lifestyle imagery, brand story
- **ARI should**: Product-led approach. Show real FX rates, real transfer times, real fee comparisons. Turkish and EU users are pragmatic about money - they want to see what they get.

### Pattern 5: Color Strategy
- Revolut: Blue accent, white/gray palette
- Nubank: Purple is THE color, everything else neutral
- Wise: Green accent, white backgrounds
- N26: Teal/green accent, clean white
- Monzo: Hot coral, warm neutrals
- **ARI should**: Commit to ONE brand color. Given Turkey/EU cross-border positioning, I recommend a deep navy (#0A1628 or similar) as the primary brand color with a clean blue (#1A6FD4) as the interactive accent. Navy communicates: established, trustworthy, international, professional. NOT teal, NOT gradients.

### Pattern 6: Navigation
- Mobile fintechs: Bottom tab bar (4-5 items)
- Web fintechs: Left sidebar (collapsible) or top nav
- **ARI web should**: Keep the left sidebar but refine it. Consider collapsible for more content space. Top bar for user info + notifications.

### Pattern 7: Empty/Loading/Error States
Best-in-class fintechs design these with care:
- Revolut: Skeleton loading screens matching final layout
- Nubank: Branded empty states with gentle illustration
- Wise: Progress indicators with estimated time
- **ARI should**: Skeleton loading states, designed empty states with CTAs, inline error messages with recovery suggestions.

---

## Part 4: Feature-Specific Design Recommendations

### 1. Landing Page

**Current problems:**
- Looks like a SaaS product page (grid background, blur circles, chip badges)
- Phone mockup with fake data doesn't build trust
- "Scroll story" is a gimmick
- Capability cards are generic

**Competitor-informed proposal:**
- **Hero**: Clean headline about Turkey-EU corridor. No decorative elements.
- **Value strip**: Three concrete benefits: speed, transparency, security
- **Trust bar**: BDDK, PSD2, KVKK logos/badges
- **Product preview**: Consider a REAL dashboard screenshot (blurred amounts) rather than a phone mockup. Shows the actual product exists.
- **Social proof**: Transfer volume, user count, average transfer time stats
- **CTA**: Single, prominent "Open an Account" button
- **Footer**: Regulatory disclosures, compliance info, contact

**Key insight from competitors**: Wise's landing page works because it shows REAL data (real rates, real fees, real speeds). ARI should show: "Send TRY 10,000 to Europe. They receive EUR X. Takes Y minutes. Fee: Z." Real calculator > marketing copy.

### 2. Auth Pages (Login/Signup)

**Current problems:**
- Too plain - functional but doesn't build confidence
- Logo should use "ARI" consistently across all pages
- 2FA field always visible on login (should be conditional)
- No trust signals on the page where users enter credentials

**Competitor-informed proposal:**
- Consistent "ARI" uppercase wordmark everywhere
- Clean centered form (Revolut-style) OR split layout (N26-style)
- 2FA: Show only after initial email/password validation succeeds and 2FA is required
- Add subtle trust indicator: "Secured with bank-grade encryption"
- Signup: Consider progressive registration - email first, then details (Revolut pattern)
- Region selector: Use flags (Turkish flag, EU flag) not just text dropdown

### 3. Dashboard Home

**Current problems:**
- Black balance cards feel disconnected from rest of UI
- "Welcome back" is generic - use actual name
- Quick action buttons are basic
- Recent activity shows minimal info (no amounts!)

**Competitor-informed proposal:**
- **Greeting**: "Good morning, [First Name]" with date (Apple-like)
- **Balances**: TRY and EUR as prominent display-size numbers. NOT in dark cards - clean, light background with the number doing the talking. Currency symbol + amount, with trend indicator if available.
- **Quick actions**: Icon-based horizontal strip: Send, Request, Convert, Bridge
- **Recent activity**: Show: icon + description, counterparty name/ID, amount with +/-, date. Not just status.
- **Account health**: Small indicators for KYC status, 2FA enabled, pending transfers

### 4. Transfer Flow

**Current problems:**
- Tab switching between domestic/cross-border is fine but could be smarter
- Amount input is standard - should be hero-sized
- FX quote display is functional but doesn't create urgency/confidence properly
- Success state is generic

**Competitor-informed proposal:**
- **Amount input**: Wise/Revolut-style large number display. As user types, number fills the screen.
- **Recipient**: Autocomplete from recent recipients + manual ID entry
- **FX Quote step**: Clear review screen showing:
  - You send: TRY X
  - Exchange rate: 1 TRY = Y EUR (with "mid-market rate" comparison)
  - Fee: Z
  - They receive: EUR W
  - Timer: Circular countdown (not just number)
  - One-tap confirm
- **Progress tracking**: After confirmation, show step-by-step progress:
  - Payment initiated
  - Compliance check (passed)
  - FX conversion (completed)
  - Settlement (in progress / completed)
  - Delivered
  This maps to ICTT bridge status: initiated -> burn -> relay -> mint -> complete
- **Success**: Animated checkmark, clear "what happens next" text, option to share receipt

### 5. Account Management

**Current problems:**
- Account list is plain
- No IBAN display
- No account details view
- Create account buttons are bare

**Competitor-informed proposal:**
- **Account cards**: Show currency, IBAN (masked with copy button), balance, status
- **Account detail view**: Full transaction history for that account, statements
- **Create flow**: Guided creation with explanation of what each account type does
- **Wallet address**: For blockchain-connected accounts, show on-chain address (collapsible)

### 6. Settings & Profile

**Current problems:**
- Basic form, no visual hierarchy
- 2FA setup uses `alert()` - not acceptable for production
- No password change
- No notification preferences

**Competitor-informed proposal:**
- **Sections**: Profile, Security, Notifications, Preferences (tabbed or accordion)
- **Security section**:
  - 2FA with proper modal/drawer for setup (QR code display, verification step)
  - Password change form
  - Active sessions list
  - Login history
- **Profile**: Show KYC status with badge, region, account tier
- **Preferences**: Language (TR/EN), currency display format, notification channels

### 7. KYC Onboarding

**Current problems:**
- Step indicator is functional but basic
- No progress persistence
- No clear explanation of WHY KYC is needed
- Emoji icons (checkmark, hourglass, X) feel unprofessional

**Competitor-informed proposal:**
- **Progress**: Horizontal stepper with clear labels and estimated time
- **Explanation**: "Regulatory requirements" is cold. Frame it as: "Verify your identity to unlock all features: send up to EUR 50,000, international transfers, earn interest"
- **Provider integration**: Full-screen modal for KYC provider widget (Veriff/Onfido)
- **Status tracking**: Clear pending/approved/rejected states with specific next steps
- **Gamification (subtle)**: Show what features unlock at each verification level

### 8. Navigation & Layout

**Current problems:**
- Sidebar uses Unicode icons
- Header is minimal to the point of empty
- No notifications
- No mobile responsiveness for dashboard
- Version tag in sidebar

**Competitor-informed proposal:**
- **Sidebar**: Proper SVG icons (Lucide recommended), collapsible on smaller screens, grouped sections (Money: Home/Transfer/Accounts, Activity: History, Account: Settings)
- **Header**: Search (transactions), notifications bell, user avatar/initials dropdown
- **Mobile**: Sidebar collapses to bottom tab bar on mobile viewports
- **Footer** (landing only): Regulatory info, compliance badges, language switcher

---

## Part 5: Design Direction Summary

### Recommended ARI Design Identity: "Institutional Precision"

Not a startup. Not a legacy bank. A modern financial institution.

**Core attributes:**
- **Precise**: Every element measured, systematic, intentional
- **Transparent**: Fees, rates, and status are always visible
- **Trustworthy**: Regulatory compliance visible, security indicators present
- **Calm**: No urgency, no hype, no marketing gimmicks
- **Professional**: Could sit alongside N26, Wise, Revolut without looking out of place

**Color**: Deep navy + clean blue + functional colors (green/red/amber)
**Type**: Inter or similar high-quality sans-serif, strict scale
**Layout**: Generous whitespace, clear hierarchy, systematic spacing
**Motion**: Subtle, purposeful, never decorative

---

*This document is a working draft. Collaborating with design-principles agent to produce the final `docs/frontend-redesign-plan.md`.*
