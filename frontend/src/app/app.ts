import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from './auth/auth';

const ROLE_LABELS = { MANAGER: 'Gérant', ACCOUNTANT: 'Comptable' } as const;

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  template: `
    <header class="topbar">
      <a routerLink="/" class="brand">eFacture <span>PME</span></a>
      @if (auth.user(); as user) {
        <nav>
          <a routerLink="/factures" routerLinkActive="active">Factures</a>
          <a routerLink="/clients" routerLinkActive="active">Clients</a>
          <a routerLink="/entreprise" routerLinkActive="active">Mon entreprise</a>
        </nav>
        <div class="session">
          <span>{{ user.displayName }} <span class="badge">{{ roleLabels[user.role] }}</span></span>
          <button type="button" class="link" (click)="logout()">Déconnexion</button>
        </div>
      }
    </header>
    <main>
      <router-outlet />
    </main>
    <footer class="muted">
      Projet de démonstration : aucune facture n’est transmise à l’administration.
    </footer>
  `,
})
export class App {
  protected readonly auth = inject(Auth);
  protected readonly roleLabels = ROLE_LABELS;
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/connexion']);
  }
}
