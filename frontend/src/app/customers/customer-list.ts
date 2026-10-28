import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../auth/auth';
import { Api, ApiError, toApiError } from '../shared/api';
import { Customer } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';
import { Icon } from '../shared/icon';
import { SkeletonTable } from '../shared/skeleton-table';

@Component({
  selector: 'app-customer-list',
  imports: [RouterLink, ErrorAlert, Icon, SkeletonTable],
  template: `
    <div class="page-header">
      <div>
        <h1>Clients</h1>
        <p class="subtitle">Le SIREN du client est obligatoire pour lui adresser une facture électronique.</p>
      </div>
      @if (isManager()) {
        <a routerLink="/clients/nouveau" class="button primary"><app-icon name="plus" [size]="16" /> Nouveau client</a>
      }
    </div>

    <app-error-alert [error]="error()" />

    @if (loading()) {
      <app-skeleton-table [columns]="4" />
    } @else if (customers().length === 0 && !error()) {
      <div class="card empty">
        <strong>Aucun client pour l’instant</strong>
        <p>Ajoutez un client avant de créer votre première facture.</p>
        @if (isManager()) {
          <a routerLink="/clients/nouveau" class="button primary"><app-icon name="plus" [size]="16" /> Ajouter un client</a>
        }
      </div>
    } @else {
      <table class="card">
        <thead>
          <tr><th>Raison sociale</th><th>SIREN</th><th>Ville</th><th></th></tr>
        </thead>
        <tbody>
          @for (customer of customers(); track customer.id) {
            <tr>
              <td>{{ customer.legalName }}</td>
              <td>
                @if (customer.siren) {
                  {{ customer.siren }}
                } @else {
                  <span class="badge warning" title="Obligatoire pour émettre une facture">SIREN manquant</span>
                }
              </td>
              <td>{{ customer.postalCode }} {{ customer.city }}</td>
              <td class="right">
                @if (isManager()) {
                  <a [routerLink]="['/clients', customer.id]">Modifier</a>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
})
export class CustomerList implements OnInit {
  private readonly api = inject(Api);
  protected readonly isManager = inject(Auth).isManager;
  protected readonly customers = signal<Customer[]>([]);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.api.listCustomers().subscribe({
      next: (customers) => {
        this.customers.set(customers);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(toApiError(e));
        this.loading.set(false);
      },
    });
  }
}
