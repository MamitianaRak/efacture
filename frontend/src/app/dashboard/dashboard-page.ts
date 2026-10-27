import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, ApiError, toApiError } from '../shared/api';
import { Dashboard } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

@Component({
  selector: 'app-dashboard-page',
  imports: [CurrencyPipe, RouterLink, ErrorAlert],
  template: `
    <div class="page-header">
      <h1>Tableau de bord</h1>
      <div class="year">
        <button type="button" (click)="load(year() - 1)" aria-label="Année précédente">‹</button>
        <strong>{{ year() }}</strong>
        <button type="button" (click)="load(year() + 1)" [disabled]="year() >= currentYear" aria-label="Année suivante">›</button>
      </div>
    </div>

    <app-error-alert [error]="error()" />

    @if (data(); as d) {
      <div class="kpis">
        <div class="card kpi">
          <span class="muted">Chiffre d’affaires HT</span>
          <strong>{{ d.revenueNet | currency: 'EUR' }}</strong>
          <small class="muted">factures émises, avoirs déduits</small>
        </div>
        <div class="card kpi">
          <span class="muted">Encaissé TTC</span>
          <strong>{{ d.collectedGross | currency: 'EUR' }}</strong>
        </div>
        <div class="card kpi">
          <span class="muted">À encaisser TTC</span>
          <strong>{{ d.receivable.totalGross | currency: 'EUR' }}</strong>
          <small class="muted">{{ d.receivable.count }} facture(s)</small>
        </div>
        <div class="card kpi" [class.alert-kpi]="d.overdue.count > 0">
          <span class="muted">En retard TTC</span>
          <strong>{{ d.overdue.totalGross | currency: 'EUR' }}</strong>
          <small class="muted">{{ d.overdue.count }} facture(s)</small>
        </div>
      </div>

      <section class="card">
        <h2>Chiffre d’affaires HT par mois</h2>
        <div class="chart" role="img" [attr.aria-label]="'Chiffre d’affaires mensuel ' + d.year">
          @for (m of d.monthlyRevenue; track m.month) {
            <div class="bar-col" [title]="months[m.month - 1] + ' : ' + (m.revenueNet | currency: 'EUR')">
              <div class="bar" [style.height.%]="barHeight(m.revenueNet)"></div>
              <span class="muted">{{ months[m.month - 1] }}</span>
            </div>
          }
        </div>
      </section>

      <div class="columns">
        <section class="card">
          <h2>Factures en retard</h2>
          @for (inv of d.overdueInvoices; track inv.id) {
            <a class="row" [routerLink]="['/factures', inv.id]">
              <span><strong>{{ inv.number }}</strong> · {{ inv.customerName }}</span>
              <span>
                {{ inv.totalGross | currency: 'EUR' }}
                <span class="badge danger">{{ inv.daysLate }} j de retard</span>
              </span>
            </a>
          } @empty {
            <p class="muted">Aucune facture en retard.</p>
          }
        </section>

        <section class="card">
          <h2>À traiter</h2>
          @for (action of d.actions; track action.invoiceId) {
            <a class="row" [routerLink]="['/factures', action.invoiceId]">
              <span>
                <strong>{{ action.number }}</strong> · {{ action.customerName }}
                @if (action.reason) {
                  <div class="muted small">{{ action.reason }}</div>
                }
              </span>
              <span class="badge warning">
                {{ actionLabels[action.type] }}
              </span>
            </a>
          } @empty {
            <p class="muted">Rien à traiter.</p>
          }
          @if (d.draftCount > 0) {
            <a class="row" routerLink="/factures">
              <span>{{ d.draftCount }} brouillon(s) en attente d’émission</span>
            </a>
          }
        </section>
      </div>
    }
  `,
  styles: `
    h2 { font-size: 1.05rem; margin: 0 0 12px; }
    .year { display: flex; align-items: center; gap: 8px; }
    .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-top: 12px; }
    .kpi { display: flex; flex-direction: column; gap: 4px; margin: 0; }
    .kpi strong { font-size: 1.4rem; }
    .alert-kpi { border-color: var(--danger); }
    .alert-kpi strong { color: var(--danger); }
    section.card { margin-top: 16px; }
    .chart { display: flex; align-items: flex-end; gap: 8px; height: 180px; }
    .bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; gap: 4px; font-size: 0.75rem; }
    .bar { width: 100%; background: var(--primary); border-radius: 4px 4px 0 0; min-height: 2px; }
    .columns { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 8px 0;
           border-bottom: 1px solid var(--border); color: inherit; text-decoration: none; }
    .row:last-child { border-bottom: none; }
    .row:hover { background: var(--bg); }
    .small { font-size: 0.85rem; }
    @media (max-width: 720px) {
      .kpis, .columns { grid-template-columns: 1fr 1fr; }
      .columns { grid-template-columns: 1fr; }
    }
  `,
})
export class DashboardPage {
  private readonly api = inject(Api);

  protected readonly months = MONTHS;
  protected readonly actionLabels = {
    REFUSED_WITHOUT_CREDIT_NOTE: 'Refusée : faire un avoir',
    REJECTED_SUBMISSION: 'Rejetée : redéposer',
    DELIVERY_FAILED: 'Envoi échoué : redéposer',
  } as const;
  protected readonly currentYear = new Date().getFullYear();
  protected readonly year = signal(this.currentYear);
  protected readonly data = signal<Dashboard | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  private readonly maxMonth = computed(() =>
    Math.max(0, ...(this.data()?.monthlyRevenue ?? []).map((m) => m.revenueNet)),
  );

  constructor() {
    this.load(this.currentYear);
  }

  load(year: number): void {
    this.year.set(year);
    this.error.set(null);
    this.api.getDashboard(year).subscribe({
      next: (data) => this.data.set(data),
      error: (e: unknown) => this.error.set(toApiError(e)),
    });
  }

  /** Bar height in % of the best month; a month with credit notes exceeding invoices shows as empty. */
  barHeight(revenue: number): number {
    const max = this.maxMonth();
    return max > 0 ? Math.max(0, (revenue / max) * 100) : 0;
  }
}
