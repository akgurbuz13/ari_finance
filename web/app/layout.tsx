import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--font-inter",
});

export const metadata: Metadata = {
  title: "Ova - Cross-border transfers between Turkey and Europe",
  description:
    "Move money between TRY and EUR with live rates, transparent fees, and real-time settlement tracking.",
  keywords: ["banking", "fintech", "Turkey", "Europe", "TRY", "EUR", "cross-border", "transfers"],
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
