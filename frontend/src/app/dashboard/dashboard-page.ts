import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, ApiError, toApiError } from '../shared/api';
import { Dashboard } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';
import { Icon } from '../shared/icon';

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

@Component({
  selector: 'app-dashboard-page',
  imports: [CurrencyPipe, RouterLink, ErrorAlert, Icon],
  template: `
    <div class="page-header">
      <div>
        <h1>Tableau de bord</h1>
        <p class="subtitle">Chiffre d’affaires, encaissements et factures qui demandent une action.</p>
      </div>
      <div class="year" role="group" aria-label="Année">
        <button type="button" class="ghost small" (click)="load(year() - 1)" aria-label="Année précédente">‹</button>
        <strong>{{ year() }}</strong>
        <button type="button" class="ghost small" (click)="load(year() + 1)" [disabled]="year() >= currentYear" aria-label="Année suivante">›</button>
      </div>
    </div>

    <app-error-alert [error]="error()" />

    @if (!data() && !error()) {
      <div class="kpis" aria-busy="true">
        @for (i of [0, 1, 2, 3]; track i) {
          <div class="card kpi">
            <span class="skeleton" style="width: 55%"></span>
            <span class="skeleton big" style="width: 75%"></span>
          </div>
        }
      </div>
      <section class="card"><span class="skeleton" style="height: 180px"></span></section>
    }

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
            <p class="all-clear"><app-icon name="check" [size]="16" /> Aucune facture en retard.</p>
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
            <p class="all-clear"><app-icon name="check" [size]="16" /> Rien à traiter.</p>
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
    .year { display: flex; align-items: center; gap: 4px; padding: 3px; background: var(--surface);
            border: 1px solid var(--border); border-radius: var(--radius-sm); }
    .year strong { min-width: 44px; text-align: center; }
    .year button { font-size: 1.1rem; padding: 2px 10px; }
    .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
    .kpi { display: flex; flex-direction: column; gap: 6px; margin: 0; }
    .kpi > span:first-child { font-size: 0.8rem; font-weight: 500; }
    .kpi strong { font-size: 1.5rem; font-weight: 650; letter-spacing: -0.01em; }
    .kpi .skeleton.big { height: 26px; }
    .alert-kpi { border-color: #f3c3c3; background: var(--danger-soft); }
    .alert-kpi strong { color: var(--danger); }
    .all-clear { display: flex; align-items: center; gap: 8px; color: var(--success); margin: 4px 0; }
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
