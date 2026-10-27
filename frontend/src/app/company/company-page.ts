import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api, ApiError, toApiError } from '../shared/api';
import { Company } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';

@Component({
  selector: 'app-company-page',
  imports: [ReactiveFormsModule, ErrorAlert],
  template: `
    <h1>Mon entreprise</h1>
    <p class="muted">Ces informations apparaissent sur chaque facture émise.</p>

    <app-error-alert [error]="error()" />
    @if (saved()) {
      <div class="notice">Informations enregistrées.</div>
    }

    <form class="card form-grid" [formGroup]="form" (ngSubmit)="save()">
      <label class="span-2">Raison sociale <input formControlName="legalName" /></label>
      <label>SIREN <input formControlName="siren" placeholder="9 chiffres" /></label>
      <label>N° de TVA intracommunautaire <input formControlName="vatNumber" placeholder="FR..." /></label>
      <label class="span-2">Adresse <input formControlName="street" /></label>
      <label>Code postal <input formControlName="postalCode" /></label>
      <label>Ville <input formControlName="city" /></label>
      <label>E-mail de facturation <input formControlName="email" type="email" /></label>
      <label>IBAN <input formControlName="iban" /></label>
      <label class="span-2">
        Conditions de paiement
        <textarea formControlName="paymentTerms" rows="3"></textarea>
        <small class="muted">Délai, pénalités de retard et indemnité forfaitaire de 40 € : mentions obligatoires.</small>
      </label>
      <div class="actions span-2">
        <button type="submit" class="primary" [disabled]="form.invalid || saving()">Enregistrer</button>
      </div>
    </form>
  `,
})
export class CompanyPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly form = inject(NonNullableFormBuilder).group({
    legalName: ['', Validators.required],
    siren: ['', [Validators.required, Validators.pattern(/^\d{9}$/)]],
    vatNumber: ['', [Validators.required, Validators.pattern(/^FR\d{11}$/)]],
    street: ['', Validators.required],
    postalCode: ['', [Validators.required, Validators.pattern(/^\d{5}$/)]],
    city: ['', Validators.required],
    email: [''],
    iban: [''],
    paymentTerms: [
      'Paiement à 30 jours. Pénalités de retard : 3 fois le taux d’intérêt légal. ' +
        'Indemnité forfaitaire pour frais de recouvrement : 40 €.',
      Validators.required,
    ],
  });
  protected readonly error = signal<ApiError | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);

  ngOnInit(): void {
    this.api.getCompany().subscribe({
      next: (company) => this.form.patchValue({ ...company, email: company.email ?? '', iban: company.iban ?? '' }),
      // 404 simply means the company is not set up yet: keep the empty form.
      error: (e: unknown) => {
        if (!(e instanceof HttpErrorResponse && e.status === 404)) {
          this.error.set(toApiError(e));
        }
      },
    });
  }

  save(): void {
    const value = this.form.getRawValue();
    const company: Company = { ...value, email: value.email || null, iban: value.iban || null };
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    this.api.saveCompany(company).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(toApiError(e));
      },
    });
  }
}
