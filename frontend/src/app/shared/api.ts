import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Company,
  Customer,
  CustomerRequest,
  Dashboard,
  Invoice,
  InvoiceRequest,
  InvoiceStatus,
  InvoiceSummary,
  PlatformStatus,
  Submission,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  getCompany(): Observable<Company> {
    return this.http.get<Company>('/api/company');
  }

  saveCompany(company: Company): Observable<Company> {
    return this.http.put<Company>('/api/company', company);
  }

  listCustomers(): Observable<Customer[]> {
    return this.http.get<Customer[]>('/api/customers');
  }

  getCustomer(id: number): Observable<Customer> {
    return this.http.get<Customer>(`/api/customers/${id}`);
  }

  createCustomer(customer: CustomerRequest): Observable<Customer> {
    return this.http.post<Customer>('/api/customers', customer);
  }

  updateCustomer(id: number, customer: CustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`/api/customers/${id}`, customer);
  }

  listInvoices(status?: InvoiceStatus): Observable<InvoiceSummary[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<InvoiceSummary[]>('/api/invoices', { params });
  }

  getInvoice(id: number): Observable<Invoice> {
    return this.http.get<Invoice>(`/api/invoices/${id}`);
  }

  createDraft(request: InvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>('/api/invoices', request);
  }

  updateDraft(id: number, request: InvoiceRequest): Observable<Invoice> {
    return this.http.put<Invoice>(`/api/invoices/${id}`, request);
  }

  deleteDraft(id: number): Observable<void> {
    return this.http.delete<void>(`/api/invoices/${id}`);
  }

  issue(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`/api/invoices/${id}/issue`, null);
  }

  createCreditNote(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`/api/invoices/${id}/credit-note`, null);
  }

  getDashboard(year: number): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard', { params: { year } });
  }

  getSubmission(invoiceId: number): Observable<Submission> {
    return this.http.get<Submission>(`/api/invoices/${invoiceId}/submission`);
  }

  submit(invoiceId: number): Observable<Submission> {
    return this.http.post<Submission>(`/api/invoices/${invoiceId}/submission`, null);
  }

  markPaid(invoiceId: number): Observable<Submission> {
    return this.http.post<Submission>(`/api/invoices/${invoiceId}/submission/payment`, null);
  }

  latestPlatformStatuses(): Observable<Record<number, PlatformStatus>> {
    return this.http.get<Record<number, PlatformStatus>>('/api/submissions/latest-statuses');
  }

  /** Fetched through HttpClient (not a plain link) so the request carries the bearer token. */
  downloadFacturX(id: number): Observable<Blob> {
    return this.http.get(`/api/invoices/${id}/facturx`, { responseType: 'blob' });
  }
}

export interface ApiError {
  message: string;
  details: string[];
}

/** Reads the backend's RFC 9457 problem details: `violations` on 422, `errors` on 400. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    const body = error.error ?? {};
    if (error.status === 0) {
      return { message: 'Le serveur ne répond pas. Le back-end est-il lancé ?', details: [] };
    }
    return {
      message: body.detail ?? `Erreur ${error.status}`,
      details: body.violations ?? body.errors ?? [],
    };
  }
  return { message: 'Erreur inattendue', details: [] };
}
