import { Routes } from '@angular/router';
import { loggedIn, managerOnly } from './auth/auth.guards';

export const routes: Routes = [
  {
    path: 'connexion',
    title: 'Connexion',
    loadComponent: () => import('./auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivateChild: [loggedIn],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      {
        path: 'tableau-de-bord',
        title: 'Tableau de bord',
        loadComponent: () => import('./dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'factures',
        title: 'Factures',
        loadComponent: () => import('./invoices/invoice-list').then((m) => m.InvoiceList),
      },
      {
        path: 'factures/nouvelle',
        title: 'Nouvelle facture',
        canActivate: [managerOnly],
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
        canActivate: [managerOnly],
        loadComponent: () => import('./customers/customer-form').then((m) => m.CustomerForm),
      },
      {
        path: 'clients/:id',
        title: 'Client',
        canActivate: [managerOnly],
        loadComponent: () => import('./customers/customer-form').then((m) => m.CustomerForm),
      },
      {
        path: 'fournisseurs',
        title: 'Factures fournisseurs',
        loadComponent: () => import('./suppliers/supplier-invoice-list').then((m) => m.SupplierInvoiceList),
      },
      {
        path: 'fournisseurs/:id',
        title: 'Facture fournisseur',
        loadComponent: () => import('./suppliers/supplier-invoice-review').then((m) => m.SupplierInvoiceReview),
      },
      {
        path: 'entreprise',
        title: 'Mon entreprise',
        loadComponent: () => import('./company/company-page').then((m) => m.CompanyPage),
      },
    ],
  },
  { path: '**', redirectTo: 'tableau-de-bord' },
];
