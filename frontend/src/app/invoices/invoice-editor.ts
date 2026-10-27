import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import { Api, ApiError, toApiError } from '../shared/api';
import { Customer, Invoice, InvoiceRequest, VAT_RATES } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';
import { computeTotals } from '../shared/totals';
import { StatusBadge } from './status-badge';

@Component({
  selector: 'app-invoice-editor',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, PercentPipe, ErrorAlert, StatusBadge],
  templateUrl: './invoice-editor.html',
})
export class InvoiceEditor implements OnInit {
  /** Bound from the route parameter; absent for a new invoice. */
  readonly id = input<string>();

  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly vatRates = VAT_RATES;
  protected readonly invoice = signal<Invoice | null>(null);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly busy = signal(false);

  protected readonly form = this.fb.group({
    customerId: this.fb.control<number | null>(null, Validators.required),
    dueDate: this.fb.control<string>(''),
    lines: this.fb.array([this.newLine()]),
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** Live preview while editing; issued invoices show the server's figures. */
  protected readonly totals = computed(() => {
    const invoice = this.invoice();
    if (invoice?.status === 'ISSUED') {
      return invoice;
    }
    const lines = (this.formValue().lines ?? []).map((l) => ({
      quantity: Number(l.quantity),
      unitPrice: Number(l.unitPrice),
      vatRate: Number(l.vatRate),
    }));
    return computeTotals(lines);
  });

  protected readonly editable = computed(() => this.invoice()?.status !== 'ISSUED');

  ngOnInit(): void {
    this.api.listCustomers().subscribe((customers) => this.customers.set(customers));
    const id = this.id();
    if (id) {
      this.api.getInvoice(Number(id)).subscribe({
        next: (invoice) => this.show(invoice),
        error: (e: unknown) => this.error.set(toApiError(e)),
      });
    } else {
      this.form.controls.dueDate.setValue(this.inDays(30));
    }
  }

  protected get lines() {
    return this.form.controls.lines;
  }

  addLine(): void {
    this.lines.push(this.newLine());
  }

  removeLine(index: number): void {
    this.lines.removeAt(index);
  }

  save(): void {
    this.run(this.saveDraft());
  }

  /** Saves pending edits first, so what gets issued is exactly what is on screen. */
  issue(): void {
    this.run(this.saveDraft().pipe(switchMap((draft) => this.api.issue(draft.id))));
  }

  deleteDraft(): void {
    const invoice = this.invoice();
    if (!invoice || !confirm('Supprimer ce brouillon ?')) {
      return;
    }
    this.busy.set(true);
    this.api.deleteDraft(invoice.id).subscribe({
      next: () => this.router.navigate(['/factures']),
      error: (e: unknown) => this.fail(e),
    });
  }

  createCreditNote(): void {
    const invoice = this.invoice();
    if (!invoice || !confirm(`Créer un avoir qui annule la facture ${invoice.number} ?`)) {
      return;
    }
    this.busy.set(true);
    this.api.createCreditNote(invoice.id).subscribe({
      next: (creditNote) => this.router.navigate(['/factures', creditNote.id]),
      error: (e: unknown) => this.fail(e),
    });
  }

  facturXUrl(invoice: Invoice): string {
    return this.api.facturXUrl(invoice.id);
  }

  private saveDraft(): Observable<Invoice> {
    const value = this.form.getRawValue();
    const request: InvoiceRequest = {
      customerId: value.customerId!,
      dueDate: value.dueDate || null,
      lines: value.lines.map((l) => ({
        description: l.description,
        quantity: Number(l.quantity),
        unitPrice: Number(l.unitPrice),
        vatRate: Number(l.vatRate),
      })),
    };
    const current = this.invoice();
    return current ? this.api.updateDraft(current.id, request) : this.api.createDraft(request);
  }

  private run(call: Observable<Invoice>): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (invoice) => {
        const isNew = !this.invoice();
        this.show(invoice);
        this.busy.set(false);
        if (isNew) {
          this.router.navigate(['/factures', invoice.id], { replaceUrl: true });
        }
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  private show(invoice: Invoice): void {
    this.invoice.set(invoice);
    this.lines.clear();
    for (const line of invoice.lines) {
      this.lines.push(this.newLine(line));
    }
    this.form.patchValue({ customerId: invoice.customerId, dueDate: invoice.dueDate ?? '' });
    if (invoice.status === 'ISSUED') {
      this.form.disable();
    }
  }

  private fail(e: unknown): void {
    this.busy.set(false);
    this.error.set(toApiError(e));
    // A failed issuance may still have saved the draft: reload to show the server state.
    const invoice = this.invoice();
    if (invoice) {
      this.api.getInvoice(invoice.id).subscribe((fresh) => this.invoice.set(fresh));
    }
  }

  private newLine(line?: { description: string; quantity: number; unitPrice: number; vatRate: number }) {
    return this.fb.group({
      description: [line?.description ?? '', Validators.required],
      quantity: [line?.quantity ?? 1, [Validators.required, Validators.min(0.001)]],
      unitPrice: [line?.unitPrice ?? 0, [Validators.required, Validators.min(0)]],
      vatRate: [line?.vatRate ?? 20, Validators.required],
    });
  }

  private inDays(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return date.toISOString().slice(0, 10);
  }
}
