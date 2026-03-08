import type { Metadata } from "next";
import { DM_Sans, Inter, Outfit } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--font-inter",
});

const dmSans = DM_Sans({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--font-dm-sans",
  weight: ["400", "500", "600", "700"],
});

const outfit = Outfit({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-outfit",
  weight: ["400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "ARI - All Your Finances. One Powerful Platform.",
  description:
    "Manage your accounts, send money instantly across borders, and sell your car safely. One platform, everything you need.",
  keywords: ["fintech", "money transfer", "vehicle sales", "cross-border", "financial platform", "secure payments", "digital banking", "multi-currency"],
  openGraph: {
    title: "ARI - All Your Finances. One Powerful Platform.",
    description:
      "Multi-currency accounts, instant transfers, and protected vehicle sales. The financial platform built for the modern world.",
    type: "website",
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className={`${inter.variable} ${dmSans.variable} ${outfit.variable}`}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
