import { computeTotals } from './totals';

describe('computeTotals', () => {
  it('groups VAT by rate like the backend', () => {
    const totals = computeTotals([
      { quantity: 1, unitPrice: 1500, vatRate: 20 },
      { quantity: 3, unitPrice: 80, vatRate: 20 },
      { quantity: 2, unitPrice: 12.5, vatRate: 5.5 },
    ]);

    // Same figures as the backend test fixture and the generated Factur-X.
    expect(totals.totalNet).toBe(1765);
    expect(totals.totalVat).toBe(349.38);
    expect(totals.totalGross).toBe(2114.38);
    expect(totals.vatBreakdown.map((b) => b.rate)).toEqual([5.5, 20]);
  });

  it('computes VAT on the rate total, not line by line', () => {
    const line = { quantity: 1, unitPrice: 0.1, vatRate: 5.5 };

    expect(computeTotals([line, line, line]).totalVat).toBe(0.02);
  });

  it('ignores incomplete lines while the user is typing', () => {
    const totals = computeTotals([
      { quantity: 0, unitPrice: 10, vatRate: 20 },
      { quantity: 2, unitPrice: Number.NaN, vatRate: 20 },
    ]);

    expect(totals.totalGross).toBe(0);
  });
});
