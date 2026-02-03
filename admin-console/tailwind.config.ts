import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Inter", "system-ui", "-apple-system", "sans-serif"],
      },
      colors: {
        admin: {
          bg: "#F9FAFB",
          surface: "#FFFFFF",
          border: "#E5E7EB",
          "border-light": "#F3F4F6",
          text: "#111827",
          "text-secondary": "#6B7280",
          "text-muted": "#9CA3AF",
          accent: "#2563EB",
          "accent-hover": "#1D4ED8",
          "accent-light": "#DBEAFE",
          "accent-bg": "#EFF6FF",
          success: "#059669",
          "success-light": "#D1FAE5",
          warning: "#D97706",
          "warning-light": "#FEF3C7",
          danger: "#DC2626",
          "danger-light": "#FEE2E2",
          "danger-hover": "#B91C1C",
          sidebar: "#1F2937",
          "sidebar-hover": "#374151",
          "sidebar-active": "#111827",
          "sidebar-text": "#D1D5DB",
          "sidebar-text-active": "#FFFFFF",
        },
      },
      boxShadow: {
        card: "0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)",
        "card-hover":
          "0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)",
        modal:
          "0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)",
      },
    },
  },
  plugins: [],
};

export default config;
