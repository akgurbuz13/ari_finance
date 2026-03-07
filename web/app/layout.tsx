import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--font-inter",
});

export const metadata: Metadata = {
  title: "ARI - The Financial Platform for Turkey & Europe",
  description:
    "Manage your accounts, send money instantly between Turkey and Europe, and sell your car safely. One platform, everything you need.",
  keywords: ["fintech", "Turkey", "Europe", "money transfer", "vehicle sales", "TRY", "EUR", "cross-border", "financial platform", "secure payments", "digital banking"],
  openGraph: {
    title: "ARI - The Financial Platform for Turkey & Europe",
    description:
      "Multi-currency accounts, instant transfers, and protected vehicle sales. The financial platform built for Turkey and Europe.",
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
