import Link from "next/link";
import FxCalculator from "../components/ui/FxCalculator";

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-ova-white">
      {/* ──── NAV ──── */}
      <nav className="border-b border-ova-200 bg-white">
        <div className="mx-auto flex h-16 max-w-landing items-center justify-between px-6">
          <Link href="/" className="ova-logo text-2xl" aria-label="Ova home">
            ova
          </Link>
          <div className="flex items-center gap-4">
            <Link
              href="/login"
              className="text-body-sm font-medium text-ova-700 transition-colors duration-fast hover:text-ova-900"
            >
              Sign in
            </Link>
            <Link
              href="/signup"
              className="inline-flex h-10 items-center rounded-xl bg-ova-navy px-5 text-body-sm font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm"
            >
              Open an account
            </Link>
          </div>
        </div>
      </nav>

      {/* ──── HERO ──── */}
      <section className="animate-hero-enter bg-white pt-24 pb-20">
        <div className="mx-auto grid max-w-landing items-start gap-16 px-6 lg:grid-cols-[1.1fr_0.9fr]">
          {/* Left column */}
          <div className="max-w-xl">
            <h1 className="text-display text-ova-navy text-balance">
              Cross-border transfers between Turkey and Europe.
            </h1>
            <p className="mt-2 text-h2 text-ova-navy font-semibold">
              Instant. Transparent. Secure.
            </p>
            <p className="mt-6 text-body-lg text-ova-700 max-w-md">
              Move money between TRY and EUR with live rates, transparent fees, and real-time settlement tracking.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-4">
              <Link
                href="/signup"
                className="inline-flex h-12 items-center rounded-xl bg-ova-navy px-6 text-body font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98]"
              >
                Open an account
              </Link>
              <Link
                href="/login"
                className="text-body-sm font-medium text-ova-blue hover:underline"
              >
                Already have an account? Sign in
              </Link>
            </div>
          </div>

          {/* Right column: FX Calculator */}
          <div>
            <FxCalculator />
          </div>
        </div>
      </section>

      {/* ──── TRUST BAR ──── */}
      <section className="bg-ova-100 py-6">
        <div className="mx-auto flex max-w-landing flex-wrap items-center justify-center gap-8 px-6 text-caption text-ova-500">
          <span className="flex items-center gap-2">
            <TrustShield />
            BDDK Licensed
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            PSD2 Authorized
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            KVKK Compliant
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            GDPR Compliant
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            MASAK Compliant
          </span>
          <span className="flex items-center gap-2">
            <TrustShield />
            AML Compliant
          </span>
        </div>
      </section>

      {/* ──── VALUE PROPOSITIONS ──── */}
      <section className="py-20">
        <div className="mx-auto max-w-form px-6">
          <div className="space-y-0 divide-y divide-ova-200">
            <ValueProp
              title="Turkey-EU in seconds"
              description="Send TRY, receive EUR. Real exchange rates, no hidden markups. Settlement confirmed in real-time."
            />
            <ValueProp
              title="Transparent fees, always"
              description="See exactly what you pay before you send. No surprises. Rate locks for 30 seconds."
            />
            <ValueProp
              title="Bank-grade security"
              description="Two-factor auth, KYC verification, sanctions screening, and encrypted infrastructure."
            />
          </div>
        </div>
      </section>

      {/* ──── FINAL CTA ──── */}
      <section className="py-20 bg-white">
        <div className="mx-auto max-w-form px-6 text-center">
          <h2 className="text-h1 text-ova-navy">
            Ready to move money without borders?
          </h2>
          <div className="mt-8">
            <Link
              href="/signup"
              className="inline-flex h-12 items-center rounded-xl bg-ova-navy px-8 text-body font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98]"
            >
              Open an account
            </Link>
          </div>
        </div>
      </section>

      {/* ──── FOOTER ──── */}
      <footer className="border-t border-ova-200 py-10">
        <div className="mx-auto max-w-landing px-6">
          <div className="flex flex-col gap-6 md:flex-row md:items-start md:justify-between">
            <div>
              <p className="ova-logo text-2xl">ova</p>
              <p className="mt-2 text-body-sm text-ova-500">
                Designed for trust. Built for movement.
              </p>
            </div>
            <div className="flex flex-wrap gap-6 text-body-sm font-medium text-ova-500">
              <a href="#" className="hover:text-ova-700 transition-colors duration-fast">Security</a>
              <a href="#" className="hover:text-ova-700 transition-colors duration-fast">Compliance</a>
              <a href="#" className="hover:text-ova-700 transition-colors duration-fast">Terms</a>
              <a href="#" className="hover:text-ova-700 transition-colors duration-fast">Contact</a>
            </div>
          </div>
          <div className="mt-8 border-t border-ova-200 pt-6">
            <p className="text-caption text-ova-400">
              Ova Financial Services, licensed by BDDK (Turkey) and authorized under PSD2 (European Union). KVKK and GDPR compliant. All transfers are subject to regulatory compliance checks.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}

/* ──── INLINE HELPER COMPONENTS ──── */

function TrustShield() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="text-ova-400">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>
  );
}

function ValueProp({ title, description }: { title: string; description: string }) {
  return (
    <div className="py-10">
      <h3 className="text-h2 text-ova-900">{title}</h3>
      <p className="mt-3 text-body-lg text-ova-500">{description}</p>
    </div>
  );
}
