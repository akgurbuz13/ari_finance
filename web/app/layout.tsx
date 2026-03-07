import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--font-inter",
});

export const metadata: Metadata = {
  title: "ARI - Secure Vehicle Sales & Cross-Border Transfers | Blockchain Escrow",
  description:
    "Replace Turkey's broken notary system with blockchain-guaranteed escrow for vehicle sales. Plus instant cross-border transfers between TRY and EUR. Powered by Avalanche.",
  keywords: ["vehicle escrow", "car sales Turkey", "blockchain escrow", "smart contract", "NFT ownership", "fintech", "Turkey", "Europe", "TRY", "EUR", "cross-border", "transfers", "Avalanche", "notary replacement"],
  openGraph: {
    title: "ARI - Secure Vehicle Sales & Cross-Border Transfers",
    description:
      "Sell your car without the notary. Send money without the bank. Blockchain-guaranteed escrow for vehicle sales and instant cross-border transfers. Powered by Avalanche.",
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
