import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../auth/auth';
import { Api, ApiError, toApiError } from '../shared/api';
import { InvoiceStatus, InvoiceSummary, PlatformStatus } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';
import { Icon } from '../shared/icon';
import { SkeletonTable } from '../shared/skeleton-table';
import { PlatformBadge } from './platform-badge';
import { StatusBadge } from './status-badge';

@Component({
  selector: 'app-invoice-list',
  imports: [RouterLink, CurrencyPipe, DatePipe, ErrorAlert, Icon, SkeletonTable, StatusBadge, PlatformBadge],
  template: `
    <div class="page-header">
      <div>
        <h1>Factures</h1>
        <p class="subtitle">Brouillons, factures émises et avoirs, avec leur statut sur la plateforme.</p>
      </div>
      @if (isManager()) {
        <a routerLink="/factures/nouvelle" class="button primary"><app-icon name="plus" [size]="16" /> Nouvelle facture</a>
      }
    </div>

    <div class="filters" role="group" aria-label="Filtrer par statut">
      <button [class.active]="status() === undefined" (click)="load(undefined)">Toutes</button>
      <button [class.active]="status() === 'DRAFT'" (click)="load('DRAFT')">Brouillons</button>
      <button [class.active]="status() === 'ISSUED'" (click)="load('ISSUED')">Émises</button>
    </div>

    <app-error-alert [error]="error()" />

    @if (loading()) {
      <app-skeleton-table [columns]="7" />
    } @else if (invoices().length === 0 && !error()) {
      <div class="card empty">
        @if (status() === undefined) {
          <strong>Aucune facture pour l’instant</strong>
          <p>Une facture reste en brouillon, modifiable, jusqu’à son émission. Le numéro est attribué à ce moment-là.</p>
          @if (isManager()) {
            <a routerLink="/factures/nouvelle" class="button primary"><app-icon name="plus" [size]="16" /> Créer une facture</a>
          }
        } @else {
          <strong>Aucune facture {{ status() === 'DRAFT' ? 'en brouillon' : 'émise' }}</strong>
          <p>Changez de filtre pour voir les autres factures.</p>
        }
      </div>
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
  protected readonly loading = signal(true);

  constructor() {
    this.load(undefined);
    this.api.latestPlatformStatuses().subscribe((statuses) => this.platformStatuses.set(statuses));
  }

  load(status: InvoiceStatus | undefined): void {
    this.status.set(status);
    this.loading.set(true);
    this.error.set(null);
    this.api.listInvoices(status).subscribe({
      next: (invoices) => {
        this.invoices.set(invoices);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(toApiError(e));
        this.loading.set(false);
      },
    });
  }
}
