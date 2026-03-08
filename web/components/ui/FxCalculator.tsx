"use client";

import { useState, useCallback } from "react";

const FX_RATE = 0.0271; // TRY -> EUR (mock)
const FEE_RATE = 0.0025; // 0.25%

export default function FxCalculator() {
  const [sendAmount, setSendAmount] = useState("10000");
  const [direction, setDirection] = useState<"TRY_EUR" | "EUR_TRY">("TRY_EUR");
  const [swapRotation, setSwapRotation] = useState(0);

  // Normalize locale input: treat dots as thousands separators if comma is present,
  // otherwise treat dot as decimal. E.g. "10.000,50" → 10000.50, "10000.50" → 10000.50
  const parseLocaleNumber = (value: string): number => {
    const hasCommaDecimal = /\d,\d{1,2}$/.test(value);
    if (hasCommaDecimal) {
      return parseFloat(value.replace(/\./g, "").replace(",", ".")) || 0;
    }
    return parseFloat(value.replace(/,/g, "")) || 0;
  };

  const numericSend = parseLocaleNumber(sendAmount);
  const fee = numericSend * FEE_RATE;

  const receiveAmount =
    direction === "TRY_EUR"
      ? (numericSend * FX_RATE).toFixed(2)
      : (numericSend / FX_RATE).toFixed(2);

  const feeDisplay =
    direction === "TRY_EUR"
      ? `₺${fee.toLocaleString("tr-TR", { minimumFractionDigits: 2 })}`
      : `€${fee.toLocaleString("de-DE", { minimumFractionDigits: 2 })}`;

  const sendCurrency = direction === "TRY_EUR" ? "TRY" : "EUR";
  const receiveCurrency = direction === "TRY_EUR" ? "EUR" : "TRY";
  const receiveSymbol = direction === "TRY_EUR" ? "€" : "₺";
  const sendFlag = direction === "TRY_EUR" ? "\u{1F1F9}\u{1F1F7}" : "\u{1F1EA}\u{1F1FA}";
  const receiveFlag = direction === "TRY_EUR" ? "\u{1F1EA}\u{1F1FA}" : "\u{1F1F9}\u{1F1F7}";

  const rateDisplay =
    direction === "TRY_EUR"
      ? `1 TRY = ${FX_RATE} EUR`
      : `1 EUR = ${(1 / FX_RATE).toFixed(2)} TRY`;

  const handleSwap = useCallback(() => {
    setSwapRotation((prev) => prev + 180);
    setDirection((d) => (d === "TRY_EUR" ? "EUR_TRY" : "TRY_EUR"));
  }, []);

  return (
    <div className="bg-white border border-ari-200 rounded-2xl p-6 shadow-card hover:shadow-card-hover transition-shadow duration-base">
      {/* Send */}
      <div>
        <label className="text-body-sm font-medium text-ari-700">You send</label>
        <div className="mt-2 flex items-center gap-3">
          <span className="text-body-sm font-medium text-ari-500 w-16">{sendFlag} {sendCurrency}</span>
          <input
            type="text"
            inputMode="decimal"
            value={sendAmount}
            onChange={(e) => setSendAmount(e.target.value.replace(/[^0-9.,]/g, ""))}
            className="flex-1 h-12 px-4 bg-ari-50 border border-ari-300 rounded-xl text-h3 text-ari-900 amount focus:outline-none focus:border-ari-blue focus:ring-2 focus:ring-ari-blue/20 transition-all duration-base"
          />
        </div>
      </div>

      {/* Swap button */}
      <div className="flex justify-center my-3">
        <button
          onClick={handleSwap}
          className="rounded-full border border-ari-200 p-2 text-ari-500 hover:bg-ari-50 hover:text-ari-700 transition-all duration-fast"
          aria-label="Swap currencies"
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{ transform: `rotate(${swapRotation}deg)`, transition: 'transform 300ms ease-out' }}
          >
            <path d="M7 16V4m0 0L3 8m4-4l4 4M17 8v12m0 0l4-4m-4 4l-4-4" />
          </svg>
        </button>
      </div>

      {/* Receive */}
      <div>
        <label className="text-body-sm font-medium text-ari-700">They receive</label>
        <div className="mt-2 flex items-center gap-3">
          <span className="text-body-sm font-medium text-ari-500 w-16">{receiveFlag} {receiveCurrency}</span>
          <div className="flex-1 h-12 px-4 bg-ari-100 border border-ari-200 rounded-xl flex items-center text-h3 text-ari-900 amount">
            {receiveSymbol}{parseFloat(receiveAmount).toLocaleString(receiveCurrency === "TRY" ? "tr-TR" : "de-DE", { minimumFractionDigits: 2 })}
          </div>
        </div>
      </div>

      {/* Details */}
      <div className="mt-5 space-y-2 border-t border-ari-200 pt-4">
        <div className="flex justify-between text-body-sm">
          <span className="text-ari-500">Rate</span>
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-ari-green animate-pulse" />
              <span className="text-caption text-ari-green font-medium">Live rate</span>
            </div>
            <span className="text-ari-900 font-medium">{rateDisplay}</span>
          </div>
        </div>
        <div className="flex justify-between text-body-sm">
          <span className="text-ari-500">Fee</span>
          <span className="text-ari-900 font-medium">{feeDisplay} (0.25%)</span>
        </div>
        <div className="flex justify-between text-body-sm">
          <span className="text-ari-500">Arrives</span>
          <span className="text-ari-900 font-medium">~2 minutes</span>
        </div>
      </div>

      {/* CTA */}
      <div className="mt-5">
        <a
          href="/signup"
          className="flex h-12 w-full items-center justify-center rounded-xl bg-ari-navy text-body font-medium text-white transition-all duration-base hover:bg-ari-navy-light hover:shadow-sm active:scale-[0.98]"
        >
          Send this amount &rarr;
        </a>
      </div>
    </div>
  );
}
