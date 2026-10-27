import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  template: `
    <header class="topbar">
      <a routerLink="/" class="brand">eFacture <span>PME</span></a>
      <nav>
        <a routerLink="/factures" routerLinkActive="active">Factures</a>
        <a routerLink="/clients" routerLinkActive="active">Clients</a>
        <a routerLink="/entreprise" routerLinkActive="active">Mon entreprise</a>
      </nav>
    </header>
    <main>
      <router-outlet />
    </main>
    <footer class="muted">
      Projet de démonstration : aucune facture n’est transmise à l’administration.
    </footer>
  `,
})
export class App {}
