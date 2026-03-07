import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--font-inter",
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
    <html lang="en" className={inter.variable}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
