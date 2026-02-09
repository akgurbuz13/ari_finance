"use client";

import { useState, useCallback } from "react";

const FX_RATE = 0.0271; // TRY -> EUR (mock)
const FEE_RATE = 0.0025; // 0.25%

export default function FxCalculator() {
  const [sendAmount, setSendAmount] = useState("10000");
  const [direction, setDirection] = useState<"TRY_EUR" | "EUR_TRY">("TRY_EUR");

  const numericSend = parseFloat(sendAmount.replace(/,/g, "")) || 0;
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

  const rateDisplay =
    direction === "TRY_EUR"
      ? `1 TRY = ${FX_RATE} EUR`
      : `1 EUR = ${(1 / FX_RATE).toFixed(2)} TRY`;

  const handleSwap = useCallback(() => {
    setDirection((d) => (d === "TRY_EUR" ? "EUR_TRY" : "TRY_EUR"));
  }, []);

  return (
    <div className="bg-white border border-ova-200 rounded-2xl p-6 shadow-card-hover">
      {/* Send */}
      <div>
        <label className="text-body-sm font-medium text-ova-700">You send</label>
        <div className="mt-2 flex items-center gap-3">
          <span className="text-body-sm font-medium text-ova-500 w-10">{sendCurrency}</span>
          <input
            type="text"
            inputMode="decimal"
            value={sendAmount}
            onChange={(e) => setSendAmount(e.target.value.replace(/[^0-9.,]/g, ""))}
            className="flex-1 h-12 px-4 bg-ova-50 border border-ova-300 rounded-xl text-h3 text-ova-900 amount focus:outline-none focus:border-ova-blue focus:ring-2 focus:ring-ova-blue/20 transition-all duration-base"
          />
        </div>
      </div>

      {/* Swap button */}
      <div className="flex justify-center my-3">
        <button
          onClick={handleSwap}
          className="rounded-full border border-ova-200 p-2 text-ova-500 hover:bg-ova-50 hover:text-ova-700 transition-all duration-fast"
          aria-label="Swap currencies"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M7 16V4m0 0L3 8m4-4l4 4M17 8v12m0 0l4-4m-4 4l-4-4" />
          </svg>
        </button>
      </div>

      {/* Receive */}
      <div>
        <label className="text-body-sm font-medium text-ova-700">They receive</label>
        <div className="mt-2 flex items-center gap-3">
          <span className="text-body-sm font-medium text-ova-500 w-10">{receiveCurrency}</span>
          <div className="flex-1 h-12 px-4 bg-ova-100 border border-ova-200 rounded-xl flex items-center text-h3 text-ova-900 amount">
            {receiveSymbol}{parseFloat(receiveAmount).toLocaleString(receiveCurrency === "TRY" ? "tr-TR" : "de-DE", { minimumFractionDigits: 2 })}
          </div>
        </div>
      </div>

      {/* Details */}
      <div className="mt-5 space-y-2 border-t border-ova-200 pt-4">
        <div className="flex justify-between text-body-sm">
          <span className="text-ova-500">Rate</span>
          <span className="text-ova-900 font-medium">{rateDisplay}</span>
        </div>
        <div className="flex justify-between text-body-sm">
          <span className="text-ova-500">Fee</span>
          <span className="text-ova-900 font-medium">{feeDisplay} (0.25%)</span>
        </div>
        <div className="flex justify-between text-body-sm">
          <span className="text-ova-500">Arrives</span>
          <span className="text-ova-900 font-medium">~2 minutes</span>
        </div>
      </div>

      {/* CTA */}
      <div className="mt-5">
        <a
          href="/signup"
          className="flex h-12 w-full items-center justify-center rounded-xl bg-ova-navy text-body font-medium text-white transition-all duration-base hover:bg-ova-navy-light hover:shadow-sm active:scale-[0.98]"
        >
          Send this amount &rarr;
        </a>
      </div>
    </div>
  );
}
