"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

type FlowStep = {
  eyebrow: string;
  title: string;
  body: string;
  metric: string;
};

type SceneRow = {
  label: string;
  value: string;
  state: "ok" | "info" | "warn";
};

type PhoneScene = {
  id: string;
  badge: string;
  title: string;
  amount: string;
  caption: string;
  rows: SceneRow[];
  footer: string;
};

const trustStats = [
  { label: "Settlement visibility", value: "Live statuses" },
  { label: "TR-EU corridor", value: "Always-on flow" },
  { label: "Ops controls", value: "Audit-ready actions" },
];

const capabilityCards = [
  {
    title: "High-trust onboarding",
    text: "Signup, identity checks, and security posture are aligned so verification feels fast without feeling weak.",
  },
  {
    title: "Precision transfers",
    text: "Domestic and cross-border movement uses explicit statuses, idempotency keys, and clear timeline visibility.",
  },
  {
    title: "FX with guardrails",
    text: "Quote windows, spread handling, and conversion checks are built into the customer journey.",
  },
  {
    title: "Compliance cockpit",
    text: "Teams review sanctions, suspicious activity, and account actions in one coherent operational workspace.",
  },
  {
    title: "Unified ledger truth",
    text: "Customer-facing balances and statements stay reliable while settlement systems run behind the scenes.",
  },
  {
    title: "Cross-chain settlement",
    text: "Outbox-driven mint, burn, and bridge events connect banking workflows with blockchain confirmation loops.",
  },
];

const flowSteps: FlowStep[] = [
  {
    eyebrow: "Step 01",
    title: "Customer starts in one tap",
    body: "A single transfer flow handles account checks, limits, and risk gates before funds move.",
    metric: "Auth + KYC + limits",
  },
  {
    eyebrow: "Step 02",
    title: "Quote locks with clarity",
    body: "The app surfaces conversion details and expiry timing so users know exactly what they will send and receive.",
    metric: "FX quote TTL + spread",
  },
  {
    eyebrow: "Step 03",
    title: "Settlement runs in sequence",
    body: "Event orchestration triggers settlement operations while callbacks synchronize customer-visible payment status.",
    metric: "Outbox -> mint/burn -> callback",
  },
  {
    eyebrow: "Step 04",
    title: "Operations stays in control",
    body: "Admins resolve KYC and compliance queues, freeze accounts if needed, and monitor audit/reconciliation trails.",
    metric: "Admin + audit + recon",
  },
];

const phoneScenes: PhoneScene[] = [
  {
    id: "scene-overview",
    badge: "Daily banking",
    title: "TRY wallet",
    amount: "TRY 248,420.12",
    caption: "Available balance",
    rows: [
      { label: "Card spend controls", value: "Active", state: "ok" },
      { label: "Transfer limit", value: "TRY 300K / day", state: "info" },
      { label: "2FA status", value: "Enabled", state: "ok" },
    ],
    footer: "Ready to send",
  },
  {
    id: "scene-quote",
    badge: "Cross-border",
    title: "Quote locked",
    amount: "TRY 120,000.00",
    caption: "You send -> EUR 3,252.00",
    rows: [
      { label: "Rate", value: "TRY/EUR 0.0271", state: "info" },
      { label: "Quote expires", value: "00:27", state: "warn" },
      { label: "Compliance check", value: "Passed", state: "ok" },
    ],
    footer: "Waiting confirmation",
  },
  {
    id: "scene-settlement",
    badge: "Settlement",
    title: "Transfer in progress",
    amount: "EUR 3,252.00",
    caption: "Recipient wallet",
    rows: [
      { label: "Burn event", value: "Confirmed", state: "ok" },
      { label: "Bridge leg", value: "Relayed", state: "info" },
      { label: "Mint event", value: "Pending", state: "warn" },
    ],
    footer: "Syncing status",
  },
  {
    id: "scene-ops",
    badge: "Operations",
    title: "Risk overview",
    amount: "24 pending KYC",
    caption: "7 open cases",
    rows: [
      { label: "Sanctions refresh", value: "Current", state: "ok" },
      { label: "Audit events (24h)", value: "1,832", state: "info" },
      { label: "Reconciliation", value: "Matched", state: "ok" },
    ],
    footer: "All systems visible",
  },
];

function ArrowIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className="transition-transform group-hover:translate-x-1"
      aria-hidden="true"
    >
      <path d="M3 8H13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9 4L13 8L9 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function SparkIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
      <path
        d="M7 1.5L8.3 4.6L11.5 5.8L8.3 7L7 10.2L5.7 7L2.5 5.8L5.7 4.6L7 1.5Z"
        stroke="currentColor"
        strokeWidth="1.2"
      />
    </svg>
  );
}

function PhoneSceneCard({ scene }: { scene: PhoneScene }) {
  return (
    <div key={scene.id} className="ova-phone-scene">
      <div className="flex items-center justify-between">
        <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-[#8fb4d2]">{scene.badge}</p>
        <p className="text-[11px] font-medium text-[#9bbad4]">09:41</p>
      </div>

      <div className="mt-4 rounded-2xl border border-[#2a567b] bg-[#0f3659] p-4">
        <p className="text-xs text-[#8ab3d5]">{scene.title}</p>
        <p className="mt-2 text-3xl font-semibold text-white">{scene.amount}</p>
        <p className="mt-1 text-xs text-[#91b8d8]">{scene.caption}</p>
      </div>

      <div className="mt-4 space-y-2.5">
        {scene.rows.map((row) => (
          <div key={row.label} className="flex items-center justify-between rounded-xl bg-[#0a2d4a] px-3 py-2.5">
            <p className="text-xs text-[#8cb0cd]">{row.label}</p>
            <span className={`ova-state-pill ova-state-${row.state}`}>{row.value}</span>
          </div>
        ))}
      </div>

      <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-[#244f70]">
        <div className="ova-phone-progress h-full w-4/5 rounded-full" />
      </div>
      <p className="mt-2 text-center text-xs font-medium text-[#8eb2cf]">{scene.footer}</p>
    </div>
  );
}

export default function LandingPage() {
  const [activeStep, setActiveStep] = useState(0);
  const [heroDepth, setHeroDepth] = useState(0);

  useEffect(() => {
    const onScroll = () => {
      setHeroDepth(Math.min(window.scrollY, 240));
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    const revealNodes = Array.from(document.querySelectorAll<HTMLElement>("[data-ova-reveal]"));
    if (!revealNodes.length) return;

    const revealObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("ova-visible");
            revealObserver.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.2, rootMargin: "0px 0px -8% 0px" }
    );

    revealNodes.forEach((node) => revealObserver.observe(node));
    return () => revealObserver.disconnect();
  }, []);

  useEffect(() => {
    const stepNodes = Array.from(document.querySelectorAll<HTMLElement>("[data-ova-step]"));
    if (!stepNodes.length) return;

    const stepObserver = new IntersectionObserver(
      (entries) => {
        let candidate: { index: number; ratio: number } | null = null;

        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const index = Number((entry.target as HTMLElement).dataset.ovaStep);
          if (Number.isNaN(index)) return;
          if (!candidate || entry.intersectionRatio > candidate.ratio) {
            candidate = { index, ratio: entry.intersectionRatio };
          }
        });

        if (candidate) {
          setActiveStep(candidate.index);
        }
      },
      {
        threshold: [0.2, 0.45, 0.7],
        rootMargin: "-18% 0px -30% 0px",
      }
    );

    stepNodes.forEach((node) => stepObserver.observe(node));
    return () => stepObserver.disconnect();
  }, []);

  const currentScene = useMemo(() => {
    return phoneScenes[Math.min(activeStep, phoneScenes.length - 1)];
  }, [activeStep]);

  return (
    <div className="ova-site min-h-screen">
      <nav className="sticky top-0 z-50 border-b border-[#d4e6f4] bg-[#f8fdff]/86 backdrop-blur-xl">
        <div className="ova-container flex h-16 items-center justify-between">
          <Link href="/" className="ova-logo" aria-label="ova home">
            ova
          </Link>

          <div className="hidden items-center gap-7 md:flex">
            <a href="#flow" className="text-sm font-medium text-[#496985] transition-colors hover:text-[#17324e]">
              Transfer journey
            </a>
            <a href="#capabilities" className="text-sm font-medium text-[#496985] transition-colors hover:text-[#17324e]">
              Capabilities
            </a>
            <a href="#security" className="text-sm font-medium text-[#496985] transition-colors hover:text-[#17324e]">
              Security
            </a>
          </div>

          <div className="flex items-center gap-3">
            <Link href="/login" className="hidden text-sm font-semibold text-[#2d4f6d] transition-colors hover:text-[#132f4a] sm:inline-flex">
              log in
            </Link>
            <Link
              href="/signup"
              className="group inline-flex items-center gap-2 rounded-full bg-[#0f3558] px-5 py-2.5 text-sm font-semibold text-white transition-all hover:bg-[#0a2a45]"
            >
              open account
              <ArrowIcon />
            </Link>
          </div>
        </div>
      </nav>

      <section className="relative overflow-hidden pb-16 pt-14 md:pb-20 md:pt-20">
        <div className="absolute inset-0 ova-surface-grid opacity-45" />
        <div className="absolute -left-16 top-4 h-52 w-52 rounded-full bg-[#70cfb6]/35 blur-3xl" />
        <div className="absolute right-4 top-14 h-60 w-60 rounded-full bg-[#99d0f4]/35 blur-3xl" />

        <div className="ova-container relative z-10 grid items-start gap-12 lg:grid-cols-[1.08fr_0.92fr]">
          <div data-ova-reveal className="ova-reveal max-w-2xl">
            <div className="ova-chip mb-6">
              <SparkIcon />
              next-generation money operations
            </div>

            <h1 className="text-balance text-4xl font-semibold leading-[1.04] text-[#0d2d4b] md:text-6xl">
              Professional banking flows,
              <span className="block font-display italic text-[#1f82cd]">designed like a product brand</span>
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-relaxed text-[#3d607f]">
              ova combines the clarity of premium fintech interfaces with operational depth: transfer orchestration, FX
              precision, compliance oversight, and settlement transparency in one experience.
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link
                href="/signup"
                className="group inline-flex items-center gap-2 rounded-full bg-[#0f3558] px-6 py-3 text-sm font-semibold text-white transition-all hover:bg-[#0a2a45]"
              >
                launch account
                <ArrowIcon />
              </Link>
              <a
                href="#flow"
                className="inline-flex items-center gap-2 rounded-full border border-[#b8d4e8] bg-white/90 px-6 py-3 text-sm font-semibold text-[#1f4565] transition-all hover:border-[#93bddb]"
              >
                see animated flow
              </a>
            </div>

            <div className="mt-8 grid gap-3 sm:grid-cols-3">
              {trustStats.map((stat) => (
                <div key={stat.label} className="rounded-xl border border-[#c9dfef] bg-white/86 px-3.5 py-3">
                  <p className="text-xs font-semibold uppercase tracking-[0.13em] text-[#6b8aa3]">{stat.label}</p>
                  <p className="mt-1 text-sm font-semibold text-[#1f4465]">{stat.value}</p>
                </div>
              ))}
            </div>
          </div>

          <div data-ova-reveal className="ova-reveal">
            <div
              className="ova-hero-desk"
              style={{
                transform: `translateY(${heroDepth * 0.08}px)`,
              }}
            >
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-[#1f4465]">corridor command</p>
                <span className="rounded-full bg-[#e4f4ec] px-3 py-1 text-xs font-semibold text-[#2d7b5d]">healthy</span>
              </div>

              <div className="mt-5 rounded-2xl border border-[#d2e6f3] bg-white/95 p-4">
                <p className="text-xs uppercase tracking-[0.13em] text-[#6a89a1]">active transfer</p>
                <p className="mt-2 text-3xl font-semibold text-[#0f2f4d]">TRY 120,000.00</p>
                <p className="mt-1 text-sm text-[#4f7290]">Istanbul -> Berlin corridor</p>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-3">
                <div className="rounded-xl border border-[#d6e7f3] bg-[#f4f9fd] p-3">
                  <p className="text-xs text-[#5d7f99]">FX quote</p>
                  <p className="mt-1 text-sm font-semibold text-[#1f4668]">TRY/EUR 0.0271</p>
                </div>
                <div className="rounded-xl border border-[#d6e7f3] bg-[#f4f9fd] p-3">
                  <p className="text-xs text-[#5d7f99]">Expected receive</p>
                  <p className="mt-1 text-sm font-semibold text-[#1f4668]">EUR 3,252.00</p>
                </div>
              </div>

              <div className="mt-4 space-y-2">
                <div className="flex items-center justify-between text-xs text-[#577894]">
                  <span>compliance pass</span>
                  <span className="font-semibold text-[#236d53]">completed</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-[#d7e8f4]">
                  <div className="ova-progress-line h-full w-[89%] rounded-full" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="capabilities" className="ova-section pt-2">
        <div className="ova-container">
          <div data-ova-reveal className="ova-reveal mb-9 max-w-2xl">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[#6788a2]">product system</p>
            <h2 className="mt-3 text-3xl font-semibold text-[#103150] md:text-4xl">
              Not just sleek screens. Real execution under the hood.
            </h2>
          </div>

          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {capabilityCards.map((card) => (
              <article key={card.title} data-ova-reveal className="ova-reveal ova-capability-card">
                <div className="mb-4 inline-flex rounded-full bg-[#e8f2fb] px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-[#2d658f]">
                  ova
                </div>
                <h3 className="text-xl font-semibold text-[#173c5f]">{card.title}</h3>
                <p className="mt-3 text-sm leading-relaxed text-[#4e718f]">{card.text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="flow" className="ova-section">
        <div className="ova-container">
          <div data-ova-reveal className="ova-reveal mb-10 max-w-2xl">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[#6788a2]">scroll story</p>
            <h2 className="mt-3 text-3xl font-semibold text-[#103150] md:text-4xl">
              As you scroll, the app state advances through the transfer lifecycle
            </h2>
          </div>

          <div className="grid items-start gap-9 lg:grid-cols-[1fr_420px]">
            <div className="space-y-4">
              {flowSteps.map((step, index) => (
                <article
                  key={step.title}
                  data-ova-step={index}
                  data-ova-reveal
                  className={`ova-reveal ova-flow-step ${activeStep === index ? "ova-flow-step-active" : ""}`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.15em] text-[#6b8aa4]">{step.eyebrow}</p>
                    <span className="rounded-full bg-[#eef6fc] px-3 py-1 text-xs font-semibold text-[#2a5f87]">{step.metric}</span>
                  </div>
                  <h3 className="mt-3 text-2xl font-semibold text-[#113556]">{step.title}</h3>
                  <p className="mt-3 text-sm leading-relaxed text-[#4e718f]">{step.body}</p>
                </article>
              ))}
            </div>

            <div data-ova-reveal className="ova-reveal lg:sticky lg:top-24">
              <div className="ova-phone-shell">
                <div className="ova-phone-notch" />
                <PhoneSceneCard scene={currentScene} />
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="security" className="ova-section pt-0">
        <div className="ova-container">
          <div data-ova-reveal className="ova-reveal rounded-3xl border border-[#c1d9ea] bg-[#0f3558] px-6 py-10 text-white md:px-10">
            <div className="grid items-start gap-8 lg:grid-cols-[1fr_0.86fr]">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[#a2c7e7]">security and operations</p>
                <h2 className="mt-3 text-3xl font-semibold leading-tight md:text-4xl">
                  Crafted like a premium fintech brand, operated like critical infrastructure
                </h2>
                <p className="mt-4 max-w-xl text-sm leading-relaxed text-[#bdd9ee]">
                  Customer interfaces stay elegant while security, compliance, and reconciliation stay explicit for internal teams.
                </p>
                <div className="mt-6 flex flex-wrap gap-3">
                  <Link
                    href="/signup"
                    className="inline-flex items-center rounded-full bg-white px-5 py-2.5 text-sm font-semibold text-[#123a5d] transition hover:bg-[#ebf5fc]"
                  >
                    open your ova account
                  </Link>
                  <Link
                    href="/login"
                    className="inline-flex items-center rounded-full border border-[#78a2c4] px-5 py-2.5 text-sm font-semibold text-[#d7ebfa] transition hover:border-[#9fc1dd]"
                  >
                    access dashboard
                  </Link>
                </div>
              </div>

              <div className="rounded-2xl border border-[#2f5c80] bg-[#0a2d4a] p-5">
                <p className="text-sm font-semibold text-[#e1f1ff]">operations pulse</p>
                <div className="mt-4 space-y-3 text-sm">
                  <div className="flex items-center justify-between rounded-lg bg-[#103a5c] px-3 py-2">
                    <span className="text-[#9ec1dc]">Pending KYC</span>
                    <span className="font-semibold text-white">24</span>
                  </div>
                  <div className="flex items-center justify-between rounded-lg bg-[#103a5c] px-3 py-2">
                    <span className="text-[#9ec1dc]">Open compliance cases</span>
                    <span className="font-semibold text-white">7</span>
                  </div>
                  <div className="flex items-center justify-between rounded-lg bg-[#103a5c] px-3 py-2">
                    <span className="text-[#9ec1dc]">Audit events (24h)</span>
                    <span className="font-semibold text-white">1,832</span>
                  </div>
                  <div className="flex items-center justify-between rounded-lg bg-[#103a5c] px-3 py-2">
                    <span className="text-[#9ec1dc]">Reconciliation state</span>
                    <span className="font-semibold text-[#7fe0bf]">matched</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <footer className="border-t border-[#c9dfee] py-10">
        <div className="ova-container flex flex-col items-start justify-between gap-4 md:flex-row md:items-center">
          <div>
            <p className="ova-logo text-3xl">ova</p>
            <p className="mt-2 text-sm text-[#567993]">Designed for trust. Built for movement.</p>
          </div>
          <div className="flex flex-wrap gap-5 text-sm font-semibold text-[#446682]">
            <a href="#" className="transition-colors hover:text-[#1a3f62]">
              security
            </a>
            <a href="#" className="transition-colors hover:text-[#1a3f62]">
              compliance
            </a>
            <a href="#" className="transition-colors hover:text-[#1a3f62]">
              terms
            </a>
            <a href="#" className="transition-colors hover:text-[#1a3f62]">
              contact
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
