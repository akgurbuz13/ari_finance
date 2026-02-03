import Link from "next/link";

function ArrowIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className="ml-2 transition-transform group-hover:translate-x-1"
    >
      <path
        d="M3.33337 8H12.6667"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M8.66663 4L12.6666 8L8.66663 12"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

const features = [
  {
    title: "Instant Transfers",
    description:
      "Send money domestically or across borders in seconds, not days. Real-time settlement powered by modern infrastructure.",
  },
  {
    title: "Multi-Currency",
    description:
      "Hold TRY and EUR in a single account. Convert between currencies at competitive mid-market rates with full transparency.",
  },
  {
    title: "Low Fees",
    description:
      "Transparent, predictable pricing. No hidden charges, no surprise markups. What you see is what you pay.",
  },
  {
    title: "Bank-Grade Security",
    description:
      "End-to-end encryption, two-factor authentication, and regulatory compliance. Your money is always protected.",
  },
];

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-black text-white">
      {/* Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 bg-black/80 backdrop-blur-md border-b border-white/10">
        <div className="ova-container flex items-center justify-between h-16">
          <Link href="/" className="ova-wordmark text-2xl text-white">
            Ova
          </Link>
          <div className="flex items-center gap-6">
            <Link
              href="/login"
              className="text-body-sm text-gray-400 hover:text-white transition-colors"
            >
              Log in
            </Link>
            <Link
              href="/signup"
              className="inline-flex items-center px-5 py-2.5 bg-white text-black text-body-sm font-medium rounded-lg hover:bg-gray-100 transition-colors"
            >
              Get started
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="relative pt-32 pb-20 md:pt-48 md:pb-32 overflow-hidden">
        {/* Subtle grid pattern */}
        <div className="absolute inset-0 opacity-[0.03]">
          <div
            className="h-full w-full"
            style={{
              backgroundImage:
                "linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)",
              backgroundSize: "64px 64px",
            }}
          />
        </div>

        <div className="ova-container relative">
          <div className="max-w-4xl mx-auto text-center">
            <div className="animate-fade-in">
              <p className="text-body-sm text-gray-500 uppercase tracking-widest mb-6">
                The future of banking
              </p>
              <h1 className="ova-wordmark text-display-xl md:text-[6rem] lg:text-[7.5rem] text-white mb-2">
                Ova
              </h1>
              <p className="text-heading-lg md:text-display text-gray-400 font-light mt-4 mb-8 text-balance">
                Banking without borders
              </p>
              <p className="text-body-lg text-gray-500 max-w-2xl mx-auto mb-12">
                One account. Multiple currencies. Instant transfers across Turkey
                and the European Union. Built for the way you move money today.
              </p>
            </div>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 animate-slide-up">
              <Link
                href="/signup"
                className="group inline-flex items-center px-8 py-4 bg-white text-black font-medium rounded-lg hover:bg-gray-100 transition-all text-body-lg"
              >
                Open an account
                <ArrowIcon />
              </Link>
              <Link
                href="/login"
                className="inline-flex items-center px-8 py-4 border border-white/20 text-white font-medium rounded-lg hover:bg-white/5 transition-all text-body-lg"
              >
                Sign in
              </Link>
            </div>
          </div>
        </div>

        {/* Decorative elements */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] rounded-full bg-gradient-radial from-white/[0.02] to-transparent pointer-events-none" />
      </section>

      {/* Features Section */}
      <section className="ova-section border-t border-white/10">
        <div className="ova-container">
          <div className="text-center mb-16">
            <h2 className="text-heading-xl md:text-display text-white font-semibold mb-4">
              Everything you need
            </h2>
            <p className="text-body-lg text-gray-500 max-w-xl mx-auto">
              A complete banking experience designed for simplicity and speed.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-4xl mx-auto">
            {features.map((feature) => (
              <div
                key={feature.title}
                className="group p-8 rounded-xl border border-white/10 hover:border-white/20 transition-all duration-300"
              >
                <h3 className="text-heading-sm text-white font-semibold mb-3">
                  {feature.title}
                </h3>
                <p className="text-body text-gray-500 leading-relaxed">
                  {feature.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="ova-section border-t border-white/10">
        <div className="ova-container text-center">
          <h2 className="text-heading-xl md:text-display text-white font-semibold mb-4">
            Ready to get started?
          </h2>
          <p className="text-body-lg text-gray-500 max-w-lg mx-auto mb-10">
            Open your Ova account in minutes. No paperwork, no branch visits.
          </p>
          <Link
            href="/signup"
            className="group inline-flex items-center px-8 py-4 bg-white text-black font-medium rounded-lg hover:bg-gray-100 transition-all text-body-lg"
          >
            Create free account
            <ArrowIcon />
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-white/10 py-12">
        <div className="ova-container">
          <div className="flex flex-col md:flex-row items-center justify-between gap-6">
            <div className="ova-wordmark text-xl text-white">Ova</div>
            <div className="flex items-center gap-8 text-body-sm text-gray-600">
              <a href="#" className="hover:text-gray-400 transition-colors">
                Privacy
              </a>
              <a href="#" className="hover:text-gray-400 transition-colors">
                Terms
              </a>
              <a href="#" className="hover:text-gray-400 transition-colors">
                Security
              </a>
              <a href="#" className="hover:text-gray-400 transition-colors">
                Contact
              </a>
            </div>
            <p className="text-caption text-gray-700">
              &copy; {new Date().getFullYear()} Ova. All rights reserved.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
