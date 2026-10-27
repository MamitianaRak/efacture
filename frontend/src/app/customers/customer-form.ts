import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, ApiError, toApiError } from '../shared/api';
import { CustomerRequest } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';

@Component({
  selector: 'app-customer-form',
  imports: [ReactiveFormsModule, RouterLink, ErrorAlert],
  template: `
    <h1>{{ id() ? 'Modifier le client' : 'Nouveau client' }}</h1>

    <app-error-alert [error]="error()" />

    <form class="card form-grid" [formGroup]="form" (ngSubmit)="save()">
      <label class="span-2">Raison sociale <input formControlName="legalName" /></label>
      <label>
        SIREN <input formControlName="siren" placeholder="9 chiffres" />
        <small class="muted">Obligatoire pour émettre une facture électronique.</small>
      </label>
      <label>N° de TVA intracommunautaire <input formControlName="vatNumber" placeholder="FR..." /></label>
      <label class="span-2">Adresse <input formControlName="street" /></label>
      <label>Code postal <input formControlName="postalCode" /></label>
      <label>Ville <input formControlName="city" /></label>
      <label class="span-2">E-mail <input formControlName="email" type="email" /></label>
      <div class="actions span-2">
        <a routerLink="/clients" class="button">Annuler</a>
        <button type="submit" class="primary" [disabled]="form.invalid || saving()">Enregistrer</button>
      </div>
    </form>
  `,
})
export class CustomerForm implements OnInit {
  /** Bound from the route parameter; absent when creating. */
  readonly id = input<string>();

  private readonly api = inject(Api);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    legalName: ['', Validators.required],
    siren: ['', Validators.pattern(/^\d{9}$/)],
    vatNumber: ['', Validators.pattern(/^FR\d{11}$/)],
    street: ['', Validators.required],
    postalCode: ['', [Validators.required, Validators.pattern(/^\d{5}$/)]],
    city: ['', Validators.required],
    email: ['', Validators.email],
  });
  protected readonly error = signal<ApiError | null>(null);
  protected readonly saving = signal(false);

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.api.getCustomer(Number(id)).subscribe({
        next: (c) =>
          this.form.patchValue({
            ...c,
            siren: c.siren ?? '',
            vatNumber: c.vatNumber ?? '',
            email: c.email ?? '',
          }),
        error: (e: unknown) => this.error.set(toApiError(e)),
      });
    }
  }

  save(): void {
    const value = this.form.getRawValue();
    const request: CustomerRequest = {
      ...value,
      siren: value.siren || null,
      vatNumber: value.vatNumber || null,
      email: value.email || null,
    };
    const id = this.id();
    const call = id ? this.api.updateCustomer(Number(id), request) : this.api.createCustomer(request);

    this.saving.set(true);
    this.error.set(null);
    call.subscribe({
      next: () => this.router.navigate(['/clients']),
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(toApiError(e));
      },
    });
  }
}
