import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'factures' },
  {
    path: 'factures',
    title: 'Factures',
    loadComponent: () => import('./invoices/invoice-list').then((m) => m.InvoiceList),
  },
  {
    path: 'factures/nouvelle',
    title: 'Nouvelle facture',
    loadComponent: () => import('./invoices/invoice-editor').then((m) => m.InvoiceEditor),
  },
  {
    path: 'factures/:id',
    title: 'Facture',
    loadComponent: () => import('./invoices/invoice-editor').then((m) => m.InvoiceEditor),
  },
  {
    path: 'clients',
    title: 'Clients',
    loadComponent: () => import('./customers/customer-list').then((m) => m.CustomerList),
  },
  {
    path: 'clients/nouveau',
    title: 'Nouveau client',
    loadComponent: () => import('./customers/customer-form').then((m) => m.CustomerForm),
  },
  {
    path: 'clients/:id',
    title: 'Client',
    loadComponent: () => import('./customers/customer-form').then((m) => m.CustomerForm),
  },
  {
    path: 'entreprise',
    title: 'Mon entreprise',
    loadComponent: () => import('./company/company-page').then((m) => m.CompanyPage),
  },
  { path: '**', redirectTo: 'factures' },
];
