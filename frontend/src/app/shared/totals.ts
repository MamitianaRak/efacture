import { VatBreakdown } from './api.models';

export interface LineInput {
  quantity: number;
  unitPrice: number;
  vatRate: number;
}

export interface Totals {
  totalNet: number;
  totalVat: number;
  totalGross: number;
  vatBreakdown: VatBreakdown[];
}

const toCents = (amount: number) => Math.round(amount * 100);

/**
 * Live preview while editing a draft, using the backend's method: VAT per rate on the rate total.
 * Computed in cents to avoid floating point drift; the server's figures remain the reference.
 */
export function computeTotals(lines: LineInput[]): Totals {
  const netCentsByRate = new Map<number, number>();
  for (const line of lines) {
    if (!(line.quantity > 0) || !(line.unitPrice >= 0)) {
      continue;
    }
    const netCents = toCents(line.quantity * line.unitPrice);
    netCentsByRate.set(line.vatRate, (netCentsByRate.get(line.vatRate) ?? 0) + netCents);
  }

  const vatBreakdown = [...netCentsByRate.entries()]
    .sort(([a], [b]) => a - b)
    .map(([rate, netCents]) => ({
      rate,
      taxableAmount: netCents / 100,
      vatAmount: Math.round((netCents * rate) / 100) / 100,
    }));

  const netCents = vatBreakdown.reduce((sum, b) => sum + toCents(b.taxableAmount), 0);
  const vatCents = vatBreakdown.reduce((sum, b) => sum + toCents(b.vatAmount), 0);
  return {
    totalNet: netCents / 100,
    totalVat: vatCents / 100,
    totalGross: (netCents + vatCents) / 100,
    vatBreakdown,
  };
}
