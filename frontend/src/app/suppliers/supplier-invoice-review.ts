import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Api, ApiError, toApiError } from '../shared/api';
import {
  ExtractedInvoice,
  PAYMENT_MEANS_LABELS,
  PaymentMeans,
  SOURCE_LABELS,
  SUPPLIER_STATUS_LABELS,
  SupplierInvoice,
} from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';

/** Which form field each check points at, so the field to look at is highlighted. */
const FIELD_OF_ISSUE: Record<string, string> = {
  INVALID_SIREN: 'supplierSiren',
  MISSING_SIREN: 'supplierSiren',
  INVALID_VAT: 'supplierVatNumber',
  VAT_SIREN_MISMATCH: 'supplierVatNumber',
  INVALID_IBAN: 'supplierIban',
  MISSING_IBAN: 'supplierIban',
  IBAN_ON_DIRECT_DEBIT: 'supplierIban',
  IBAN_NOT_IN_DOCUMENT: 'supplierIban',
  SIREN_NOT_IN_DOCUMENT: 'supplierSiren',
  VAT_NOT_IN_DOCUMENT: 'supplierVatNumber',
  NUMBER_NOT_IN_DOCUMENT: 'invoiceNumber',
  DUPLICATE: 'invoiceNumber',
  DUE_BEFORE_ISSUE: 'dueDate',
  TOTALS_MISMATCH: 'totalGross',
  LINES_MISMATCH: 'totalNet',
};

@Component({
  selector: 'app-supplier-invoice-review',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, ErrorAlert],
  templateUrl: './supplier-invoice-review.html',
  styleUrl: './supplier-invoice-review.scss',
})
export class SupplierInvoiceReview {
  /** Bound from the route parameter. */
  readonly id = input.required<string>();

  private readonly api = inject(Api);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly fb = inject(FormBuilder);

  protected readonly statusLabels = SUPPLIER_STATUS_LABELS;
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly paymentMeansLabels = PAYMENT_MEANS_LABELS;
  protected readonly paymentMeansOptions = Object.keys(PAYMENT_MEANS_LABELS) as PaymentMeans[];
  protected readonly invoice = signal<SupplierInvoice | null>(null);
  protected readonly fileUrl = signal<SafeResourceUrl | null>(null);
  protected readonly isImage = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  private objectUrl: string | null = null;

  protected readonly form = this.fb.group({
    supplierName: this.fb.control<string | null>(null),
    supplierSiren: this.fb.control<string | null>(null),
    supplierVatNumber: this.fb.control<string | null>(null),
    supplierIban: this.fb.control<string | null>(null),
    paymentMeans: this.fb.control<PaymentMeans>('UNKNOWN'),
    invoiceNumber: this.fb.control<string | null>(null),
    issueDate: this.fb.control<string | null>(null),
    dueDate: this.fb.control<string | null>(null),
    currency: this.fb.control<string | null>('EUR'),
    lines: this.fb.array<ReturnType<SupplierInvoiceReview['newLine']>>([]),
    totalNet: this.fb.control<number | null>(null),
    totalVat: this.fb.control<number | null>(null),
    totalGross: this.fb.control<number | null>(null),
  });

  protected readonly open = computed(() => {
    const status = this.invoice()?.status;
    return status === 'NEEDS_REVIEW' || status === 'CHECKED';
  });
  protected readonly errors = computed(() => (this.invoice()?.issues ?? []).filter((i) => i.severity === 'ERROR'));
  protected readonly warnings = computed(() => (this.invoice()?.issues ?? []).filter((i) => i.severity === 'WARNING'));
  private readonly flaggedFields = computed(
    () => new Set((this.invoice()?.issues ?? []).map((i) => FIELD_OF_ISSUE[i.code]).filter(Boolean)),
  );

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
    inject(DestroyRef).onDestroy(() => this.revokeFile());
  }

  protected get lines() {
    return this.form.controls.lines;
  }

  flagged(field: string): boolean {
    return this.flaggedFields().has(field);
  }

  addLine(): void {
    this.lines.push(this.newLine());
  }

  removeLine(index: number): void {
    this.lines.removeAt(index);
  }

  save(): void {
    this.run(this.api.correctSupplierInvoice(this.invoice()!.id, this.formData()));
  }

  validate(): void {
    this.run(this.api.validateSupplierInvoice(this.invoice()!.id));
  }

  reject(): void {
    const reason = prompt('Motif du rejet (par exemple : facture en double, montant contesté) :');
    if (reason?.trim()) {
      this.run(this.api.rejectSupplierInvoice(this.invoice()!.id, reason.trim()));
    }
  }

  private load(id: number): void {
    this.error.set(null);
    this.api.getSupplierInvoice(id).subscribe({
      next: (invoice) => {
        this.show(invoice);
        this.api.supplierInvoiceFile(id).subscribe((file) => this.showFile(file));
      },
      error: (e: unknown) => this.error.set(toApiError(e)),
    });
  }

  private run(call: Observable<SupplierInvoice>): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (invoice) => {
        this.show(invoice);
        this.busy.set(false);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toApiError(e));
      },
    });
  }

  private show(invoice: SupplierInvoice): void {
    this.invoice.set(invoice);
    this.lines.clear();
    invoice.data.lines.forEach((line) => this.lines.push(this.newLine(line)));
    const { lines: _ignored, ...fields } = invoice.data;
    this.form.patchValue(fields);
    if (this.open()) {
      this.form.enable();
    } else {
      this.form.disable();
    }
  }

  /** The original file is fetched with the bearer token, then shown from a local object URL. */
  private showFile(file: Blob): void {
    this.revokeFile();
    this.objectUrl = URL.createObjectURL(file);
    this.isImage.set(file.type.startsWith('image/'));
    this.fileUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
  }

  private revokeFile(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }

  private formData(): ExtractedInvoice {
    const value = this.form.getRawValue();
    const blankToNull = (v: string | null) => (v && v.trim() ? v.trim() : null);
    return {
      supplierName: blankToNull(value.supplierName),
      supplierSiren: blankToNull(value.supplierSiren),
      supplierVatNumber: blankToNull(value.supplierVatNumber),
      supplierIban: blankToNull(value.supplierIban),
      paymentMeans: value.paymentMeans ?? 'UNKNOWN',
      invoiceNumber: blankToNull(value.invoiceNumber),
      issueDate: blankToNull(value.issueDate),
      dueDate: blankToNull(value.dueDate),
      currency: blankToNull(value.currency) ?? 'EUR',
      lines: value.lines.map((l) => ({
        description: blankToNull(l.description),
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        vatRate: l.vatRate,
        netAmount: l.netAmount,
      })),
      totalNet: value.totalNet,
      totalVat: value.totalVat,
      totalGross: value.totalGross,
    };
  }

  private newLine(line?: ExtractedInvoice['lines'][number]) {
    return this.fb.group({
      description: this.fb.control<string | null>(line?.description ?? null),
      quantity: this.fb.control<number | null>(line?.quantity ?? null),
      unitPrice: this.fb.control<number | null>(line?.unitPrice ?? null),
      vatRate: this.fb.control<number | null>(line?.vatRate ?? null),
      netAmount: this.fb.control<number | null>(line?.netAmount ?? null),
    });
  }
}
