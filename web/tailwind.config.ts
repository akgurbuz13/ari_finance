import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        ova: {
          // Brand
          navy: "#0D1B2A",
          "navy-light": "#1B2D3E",
          // Interactive
          blue: "#1A6FD4",
          "blue-hover": "#1560B8",
          "blue-light": "#EBF4FF",
          // Neutral scale
          950: "#0A0A0A",
          900: "#171717",
          700: "#404040",
          500: "#737373",
          400: "#A3A3A3",
          300: "#D4D4D4",
          200: "#E5E5E5",
          100: "#F5F5F5",
          50: "#FAFAFA",
          white: "#FFFFFF",
          // Functional
          green: "#16803C",
          "green-light": "#F0FDF4",
          red: "#DC2626",
          "red-light": "#FEF2F2",
          amber: "#B45309",
          "amber-light": "#FFFBEB",
        },
      },
      fontFamily: {
        sans: ["Inter", "-apple-system", "SF Pro Display", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "SF Mono", "monospace"],
      },
      fontSize: {
        display: ["3rem", { lineHeight: "1", letterSpacing: "-0.025em", fontWeight: "600" }],
        h1: ["2.25rem", { lineHeight: "1.15", letterSpacing: "-0.02em", fontWeight: "600" }],
        h2: ["1.75rem", { lineHeight: "1.2", letterSpacing: "-0.015em", fontWeight: "600" }],
        h3: ["1.25rem", { lineHeight: "1.3", letterSpacing: "-0.01em", fontWeight: "500" }],
        "body-lg": ["1.0625rem", { lineHeight: "1.6", letterSpacing: "0", fontWeight: "400" }],
        body: ["0.9375rem", { lineHeight: "1.6", letterSpacing: "0", fontWeight: "400" }],
        "body-sm": ["0.8125rem", { lineHeight: "1.5", letterSpacing: "0", fontWeight: "400" }],
        caption: ["0.6875rem", { lineHeight: "1.4", letterSpacing: "0.02em", fontWeight: "500" }],
      },
      spacing: {
        "4.5": "1.125rem",
        "13": "3.25rem",
        "15": "3.75rem",
        "18": "4.5rem",
        "22": "5.5rem",
        "26": "6.5rem",
      },
      maxWidth: {
        form: "720px",
        dashboard: "960px",
        landing: "1200px",
      },
      borderRadius: {
        xl: "1rem",
        "2xl": "1.5rem",
        full: "9999px",
      },
      boxShadow: {
        card: "0 1px 3px rgba(0,0,0,0.04)",
        "card-hover": "0 2px 8px rgba(0,0,0,0.06)",
        sm: "0 1px 2px 0 rgba(0,0,0,0.05)",
      },
      backgroundImage: {
        "gradient-card": "linear-gradient(135deg, #0D1B2A 0%, #1B2D3E 100%)",
        "gradient-subtle": "linear-gradient(180deg, #FFFFFF 0%, #FAFAFA 100%)",
        "gradient-radial": "radial-gradient(circle, var(--tw-gradient-stops))",
      },
      animation: {
        "hero-enter": "heroEnter 600ms ease-out",
        "fade-in": "fadeIn 300ms ease-out",
        "fade-in-up": "fadeInUp 400ms ease-out",
        "slide-in-right": "slideInRight 300ms ease-out",
        "scale-in": "scaleIn 200ms ease-out",
        shimmer: "shimmer 2s linear infinite",
        float: "float 6s ease-in-out infinite",
        "spin-slow": "spin 3s linear infinite",
      },
      keyframes: {
        heroEnter: {
          "0%": { opacity: "0", transform: "translateY(12px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        fadeIn: {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        fadeInUp: {
          "0%": { opacity: "0", transform: "translateY(8px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        slideInRight: {
          "0%": { opacity: "0", transform: "translateX(-8px)" },
          "100%": { opacity: "1", transform: "translateX(0)" },
        },
        scaleIn: {
          "0%": { opacity: "0", transform: "scale(0.95)" },
          "100%": { opacity: "1", transform: "scale(1)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
        float: {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-6px)" },
        },
      },
      transitionDuration: {
        fast: "150ms",
        base: "200ms",
        slow: "300ms",
      },
      transitionTimingFunction: {
        out: "ease-out",
      },
    },
  },
  plugins: [],
};

export default config;
