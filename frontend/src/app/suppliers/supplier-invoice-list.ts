import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api, ApiError, toApiError } from '../shared/api';
import { SOURCE_LABELS, SUPPLIER_STATUS_LABELS, SupplierInvoiceSummary } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';

@Component({
  selector: 'app-supplier-invoice-list',
  imports: [RouterLink, CurrencyPipe, DatePipe, ErrorAlert],
  template: `
    <div class="page-header">
      <h1>Factures fournisseurs</h1>
    </div>
    <p class="muted">
      Un Factur-X est lu directement depuis son XML. Un PDF classique, un scan ou une photo est lu par l’IA,
      puis chaque donnée est contrôlée avant votre validation.
    </p>

    <label class="card dropzone" [class.busy]="uploading()" (dragover)="$event.preventDefault()" (drop)="drop($event)">
      @if (uploading()) {
        <strong>Lecture de la facture en cours…</strong>
        <span class="muted">Une lecture par l’IA prend de 10 à 20 secondes.</span>
      } @else {
        <strong>Déposez une facture ici ou cliquez pour choisir un fichier</strong>
        <span class="muted">PDF, PNG ou JPEG, 5 Mo maximum</span>
      }
      <input type="file" accept="application/pdf,image/png,image/jpeg" (change)="pick($event)" [disabled]="uploading()" hidden />
    </label>

    <app-error-alert [error]="error()" />

    @if (invoices().length === 0 && !error()) {
      <div class="card empty">Aucune facture fournisseur reçue.</div>
    } @else {
      <table class="card">
        <thead>
          <tr><th>Fournisseur</th><th>Numéro</th><th>Date</th><th>Lecture</th><th>Statut</th><th class="right">Total TTC</th></tr>
        </thead>
        <tbody>
          @for (invoice of invoices(); track invoice.id) {
            <tr class="clickable" [routerLink]="['/fournisseurs', invoice.id]">
              <td><a [routerLink]="['/fournisseurs', invoice.id]">{{ invoice.supplierName ?? 'À compléter' }}</a></td>
              <td>{{ invoice.invoiceNumber }}</td>
              <td>{{ invoice.issueDate | date: 'dd/MM/yyyy' }}</td>
              <td><span class="badge">{{ sourceLabels[invoice.source] }}</span></td>
              <td>
                <span class="badge" [class.success]="invoice.status === 'VALIDATED' || invoice.status === 'CHECKED'"
                      [class.warning]="invoice.status === 'NEEDS_REVIEW'" [class.danger]="invoice.status === 'REJECTED'">
                  {{ statusLabels[invoice.status] }}
                </span>
                @if (invoice.errorCount > 0) {
                  <span class="muted small">{{ invoice.errorCount }} erreur(s)</span>
                }
              </td>
              <td class="right">{{ invoice.totalGross | currency: 'EUR' }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: `
    .dropzone { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 28px;
                border: 2px dashed var(--border); cursor: pointer; font-weight: 400; text-align: center; }
    .dropzone:hover { border-color: var(--primary); }
    .dropzone.busy { cursor: progress; border-color: var(--primary); }
    .small { font-size: 0.8rem; margin-left: 6px; }
  `,
})
export class SupplierInvoiceList {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  protected readonly statusLabels = SUPPLIER_STATUS_LABELS;
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly invoices = signal<SupplierInvoiceSummary[]>([]);
  protected readonly uploading = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  constructor() {
    this.api.listSupplierInvoices().subscribe({
      next: (invoices) => this.invoices.set(invoices),
      error: (e: unknown) => this.error.set(toApiError(e)),
    });
  }

  pick(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.upload(file);
    }
  }

  drop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file && !this.uploading()) {
      this.upload(file);
    }
  }

  private upload(file: File): void {
    this.uploading.set(true);
    this.error.set(null);
    this.api.uploadSupplierInvoice(file).subscribe({
      next: (invoice) => this.router.navigate(['/fournisseurs', invoice.id]),
      error: (e: unknown) => {
        this.uploading.set(false);
        this.error.set(toApiError(e));
      },
    });
  }
}
