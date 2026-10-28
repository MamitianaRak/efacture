import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import { Auth } from '../auth/auth';
import { Api, ApiError, toApiError } from '../shared/api';
import { Dialogs } from '../shared/dialog';
import { Icon } from '../shared/icon';
import { Toasts } from '../shared/toast';
import { Customer, Invoice, InvoiceRequest, VAT_RATES } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';
import { computeTotals } from '../shared/totals';
import { PlatformPanel } from './platform-panel';
import { StatusBadge } from './status-badge';

@Component({
  selector: 'app-invoice-editor',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, PercentPipe, ErrorAlert, StatusBadge, PlatformPanel, Icon],
  templateUrl: './invoice-editor.html',
})
export class InvoiceEditor {
  /** Bound from the route parameter; absent for a new invoice. */
  readonly id = input<string>();

  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);

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
    if (invoice && !this.editable()) {
      return invoice;
    }
    const lines = (this.formValue().lines ?? []).map((l) => ({
      quantity: Number(l.quantity),
      unitPrice: Number(l.unitPrice),
      vatRate: Number(l.vatRate),
    }));
    return computeTotals(lines);
  });

  protected readonly isManager = inject(Auth).isManager;

  /** Drafts are edited by the manager; the accountant only works on credit notes. */
  protected readonly editable = computed(() => this.invoice()?.status !== 'ISSUED' && this.isManager());

  /** The accountant may issue a credit note draft, but not an invoice. */
  protected readonly canIssue = computed(() => {
    const invoice = this.invoice();
    return invoice?.status !== 'ISSUED' && (this.isManager() || invoice?.type === 'CREDIT_NOTE');
  });

  constructor() {
    this.api.listCustomers().subscribe((customers) => this.customers.set(customers));
    // The router reuses this component from /factures/2 to /factures/5 (e.g. after creating a credit note):
    // reload whenever the id changes, not only on creation.
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });
  }

  private load(id: string | undefined): void {
    this.error.set(null);
    if (!id) {
      this.form.controls.dueDate.setValue(this.inDays(30));
      return;
    }
    if (this.invoice()?.id === Number(id)) {
      return;
    }
    this.api.getInvoice(Number(id)).subscribe({
      next: (invoice) => this.show(invoice),
      error: (e: unknown) => this.error.set(toApiError(e)),
    });
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
    this.run(this.saveDraft(), () => 'Brouillon enregistré');
  }

  /** Saves pending edits first, so what gets issued is exactly what is on screen. */
  issue(): void {
    const current = this.invoice();
    if (current && !this.editable()) {
      // Read-only for this user (accountant on a credit note): nothing to save.
      this.run(this.api.issue(current.id), issuedMessage);
      return;
    }
    this.run(this.saveDraft().pipe(switchMap((draft) => this.api.issue(draft.id))),
        issuedMessage);
  }

  async deleteDraft(): Promise<void> {
    const invoice = this.invoice();
    const confirmed = invoice && (await this.dialogs.confirm('Supprimer ce brouillon ?',
        'Le brouillon sera définitivement supprimé. Aucun numéro n’ayant été attribué, la numérotation n’est pas affectée.',
        { confirmLabel: 'Supprimer', danger: true }));
    if (!invoice || !confirmed) {
      return;
    }
    this.busy.set(true);
    this.api.deleteDraft(invoice.id).subscribe({
      next: () => {
        this.toasts.success('Brouillon supprimé');
        this.router.navigate(['/factures']);
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  async createCreditNote(): Promise<void> {
    const invoice = this.invoice();
    const confirmed = invoice && (await this.dialogs.confirm(`Annuler la facture ${invoice.number} ?`,
        'Un avoir du même montant sera créé en brouillon. Une facture émise ne peut pas être supprimée : l’avoir est la seule façon légale de l’annuler.',
        { confirmLabel: 'Créer l’avoir', danger: true }));
    if (!invoice || !confirmed) {
      return;
    }
    this.busy.set(true);
    this.api.createCreditNote(invoice.id).subscribe({
      next: (creditNote) => {
        this.toasts.success('Avoir créé en brouillon');
        this.router.navigate(['/factures', creditNote.id]);
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  downloadFacturX(invoice: Invoice): void {
    this.api.downloadFacturX(invoice.id).subscribe({
      next: (pdf) => {
        const url = URL.createObjectURL(pdf);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${invoice.number}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (e: unknown) => this.error.set(toApiError(e)),
    });
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

  private run(call: Observable<Invoice>, successMessage: (invoice: Invoice) => string): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (invoice) => {
        const isNew = !this.invoice();
        this.show(invoice);
        this.busy.set(false);
        this.toasts.success(successMessage(invoice));
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
    if (this.editable()) {
      this.form.enable();
    } else {
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

function issuedMessage(invoice: Invoice): string {
  return invoice.type === 'CREDIT_NOTE' ? `Avoir ${invoice.number} émis` : `Facture ${invoice.number} émise`;
}
