import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, ApiError, toApiError } from '../shared/api';
import { Customer } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';

@Component({
  selector: 'app-customer-list',
  imports: [RouterLink, ErrorAlert],
  template: `
    <div class="page-header">
      <h1>Clients</h1>
      <a routerLink="/clients/nouveau" class="button primary">Nouveau client</a>
    </div>

    <app-error-alert [error]="error()" />

    @if (customers().length === 0 && !error()) {
      <div class="card empty">Aucun client pour l’instant.</div>
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
              <td class="right"><a [routerLink]="['/clients', customer.id]">Modifier</a></td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
})
export class CustomerList implements OnInit {
  private readonly api = inject(Api);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly error = signal<ApiError | null>(null);

  ngOnInit(): void {
    this.api.listCustomers().subscribe({
      next: (customers) => this.customers.set(customers),
      error: (e: unknown) => this.error.set(toApiError(e)),
    });
  }
}
