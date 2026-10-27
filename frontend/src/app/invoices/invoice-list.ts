import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../auth/auth';
import { Api, ApiError, toApiError } from '../shared/api';
import { InvoiceStatus, InvoiceSummary, PlatformStatus } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';
import { PlatformBadge } from './platform-badge';
import { StatusBadge } from './status-badge';

@Component({
  selector: 'app-invoice-list',
  imports: [RouterLink, CurrencyPipe, DatePipe, ErrorAlert, StatusBadge, PlatformBadge],
  template: `
    <div class="page-header">
      <h1>Factures</h1>
      @if (isManager()) {
        <a routerLink="/factures/nouvelle" class="button primary">Nouvelle facture</a>
      }
    </div>

    <div class="filters" role="group" aria-label="Filtrer par statut">
      <button [class.active]="status() === undefined" (click)="load(undefined)">Toutes</button>
      <button [class.active]="status() === 'DRAFT'" (click)="load('DRAFT')">Brouillons</button>
      <button [class.active]="status() === 'ISSUED'" (click)="load('ISSUED')">Émises</button>
    </div>

    <app-error-alert [error]="error()" />

    @if (invoices().length === 0 && !error()) {
      <div class="card empty">Aucune facture.</div>
    } @else {
      <table class="card">
        <thead>
          <tr><th>Numéro</th><th>Client</th><th>Émise le</th><th>Échéance</th><th>Statut</th><th>Plateforme</th><th class="right">Total TTC</th></tr>
        </thead>
        <tbody>
          @for (invoice of invoices(); track invoice.id) {
            <tr class="clickable" [routerLink]="['/factures', invoice.id]">
              <td>
                <a [routerLink]="['/factures', invoice.id]">{{ invoice.number ?? 'Brouillon' }}</a>
                @if (invoice.type === 'CREDIT_NOTE') { <span class="badge">Avoir</span> }
              </td>
              <td>{{ invoice.customerName }}</td>
              <td>{{ invoice.issueDate | date: 'dd/MM/yyyy' }}</td>
              <td>{{ invoice.dueDate | date: 'dd/MM/yyyy' }}</td>
              <td><app-status-badge [status]="invoice.status" /></td>
              <td>
                @if (platformStatuses()[invoice.id]; as platformStatus) {
                  <app-platform-badge [status]="platformStatus" />
                }
              </td>
              <td class="right">{{ invoice.totalGross | currency: 'EUR' }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
})
export class InvoiceList {
  private readonly api = inject(Api);
  protected readonly isManager = inject(Auth).isManager;
  protected readonly invoices = signal<InvoiceSummary[]>([]);
  protected readonly platformStatuses = signal<Record<number, PlatformStatus>>({});
  protected readonly status = signal<InvoiceStatus | undefined>(undefined);
  protected readonly error = signal<ApiError | null>(null);

  constructor() {
    this.load(undefined);
    this.api.latestPlatformStatuses().subscribe((statuses) => this.platformStatuses.set(statuses));
  }

  load(status: InvoiceStatus | undefined): void {
    this.status.set(status);
    this.api.listInvoices(status).subscribe({
      next: (invoices) => this.invoices.set(invoices),
      error: (e: unknown) => this.error.set(toApiError(e)),
    });
  }
}
