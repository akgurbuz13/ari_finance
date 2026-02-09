import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Ova - Banking Without Borders",
  description:
    "The modern fintech platform for instant transfers, multi-currency accounts, and seamless cross-border payments.",
  keywords: ["banking", "fintech", "transfers", "multi-currency", "payments"],
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
