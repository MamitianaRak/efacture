import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Auth } from './auth/auth';
import { DialogHost } from './shared/dialog';
import { Icon } from './shared/icon';
import { ToastHost } from './shared/toast';

const ROLE_LABELS = { MANAGER: 'Gérant', ACCOUNTANT: 'Comptable' } as const;

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

const NAVIGATION: { section: string; items: NavItem[] }[] = [
  { section: 'Pilotage', items: [{ path: '/tableau-de-bord', label: 'Tableau de bord', icon: 'dashboard' }] },
  {
    section: 'Ventes',
    items: [
      { path: '/factures', label: 'Factures', icon: 'invoice' },
      { path: '/clients', label: 'Clients', icon: 'users' },
    ],
  },
  { section: 'Achats', items: [{ path: '/fournisseurs', label: 'Factures fournisseurs', icon: 'inbox' }] },
  { section: 'Paramètres', items: [{ path: '/entreprise', label: 'Mon entreprise', icon: 'building' }] },
];

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, ToastHost, DialogHost],
  selector: 'app-root',
  template: `
    @if (auth.user(); as user) {
      <div class="shell" [class.menu-open]="menuOpen()">
        <aside class="sidebar">
          <a routerLink="/" class="brand">
            <span class="logo">eF</span>
            <span>eFacture <strong>PME</strong></span>
          </a>
          <nav aria-label="Navigation principale">
            @for (group of navigation; track group.section) {
              <p class="nav-section">{{ group.section }}</p>
              @for (item of group.items; track item.path) {
                <a [routerLink]="item.path" routerLinkActive="active">
                  <app-icon [name]="item.icon" />
                  {{ item.label }}
                </a>
              }
            }
          </nav>
          <div class="account">
            <span class="avatar">{{ user.displayName.charAt(0) }}</span>
            <div class="who">
              <strong>{{ user.displayName }}</strong>
              <span class="muted small">{{ roleLabels[user.role] }}</span>
            </div>
            <button type="button" class="ghost small" (click)="logout()" title="Se déconnecter" aria-label="Se déconnecter">
              <app-icon name="logout" [size]="16" />
            </button>
          </div>
        </aside>
        <div class="backdrop" (click)="menuOpen.set(false)"></div>

        <div class="content">
          <header class="mobile-bar">
            <button type="button" class="ghost" (click)="menuOpen.set(true)" aria-label="Ouvrir le menu">
              <app-icon name="menu" />
            </button>
            <span class="brand-text">eFacture <strong>PME</strong></span>
          </header>
          <div class="demo-banner">
            <app-icon name="info" [size]="14" />
            Démonstration : la plateforme agréée est simulée, aucune facture n’est transmise à l’administration.
          </div>
          <main>
            <router-outlet />
          </main>
        </div>
      </div>
    } @else {
      <router-outlet />
    }
    <app-toasts />
    <app-dialog-host />
  `,
  styles: `
    .shell { display: flex; min-height: 100vh; }

    .sidebar {
      position: sticky; top: 0; height: 100vh; width: var(--sidebar-width); flex-shrink: 0;
      display: flex; flex-direction: column; background: #0f172a; color: #cbd5e1; padding: 20px 14px;
    }
    .brand { display: flex; align-items: center; gap: 10px; color: #fff; font-size: 1.05rem; padding: 0 8px 20px; }
    .brand:hover { text-decoration: none; }
    .logo {
      width: 32px; height: 32px; border-radius: 8px; background: var(--primary); color: #fff;
      display: grid; place-items: center; font-weight: 700; font-size: 0.85rem;
    }
    nav { flex: 1; overflow-y: auto; }
    .nav-section {
      font-size: 0.7rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.08em;
      color: #64748b; margin: 18px 8px 6px;
    }
    nav a {
      display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 6px;
      color: #cbd5e1; font-weight: 500; margin-bottom: 2px;
    }
    nav a:hover { background: rgba(255, 255, 255, 0.06); color: #fff; text-decoration: none; }
    nav a.active { background: rgba(47, 91, 234, 0.25); color: #fff; }

    .account {
      display: flex; align-items: center; gap: 10px; padding: 12px 8px 0;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
    }
    .avatar {
      width: 32px; height: 32px; border-radius: 50%; background: #334155; color: #fff;
      display: grid; place-items: center; font-weight: 600;
    }
    .who { flex: 1; display: flex; flex-direction: column; min-width: 0; line-height: 1.3; }
    .who strong { color: #fff; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .account button { color: #94a3b8; }
    .account button:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }

    .content { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .demo-banner {
      display: flex; align-items: center; justify-content: center; gap: 6px; padding: 6px 16px;
      background: var(--info-soft); color: var(--info); font-size: 0.8rem; border-bottom: 1px solid #d6e2ff;
    }
    main { width: 100%; max-width: 1180px; margin: 0 auto; padding: 28px 32px 56px; }

    .mobile-bar, .backdrop { display: none; }

    @media (max-width: 960px) {
      .sidebar {
        position: fixed; z-index: 50; left: 0; transform: translateX(-100%); transition: transform 0.2s ease;
      }
      .menu-open .sidebar { transform: none; }
      .menu-open .backdrop { display: block; position: fixed; inset: 0; z-index: 40; background: rgba(15, 23, 42, 0.45); }
      .mobile-bar {
        display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--surface);
        border-bottom: 1px solid var(--border);
      }
      main { padding: 20px 16px 48px; }
    }
  `,
})
export class App {
  protected readonly auth = inject(Auth);
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly navigation = NAVIGATION;
  protected readonly menuOpen = signal(false);
  private readonly router = inject(Router);

  constructor() {
    // Close the mobile menu once a page is chosen.
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.menuOpen.set(false));
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/connexion']);
  }
}
