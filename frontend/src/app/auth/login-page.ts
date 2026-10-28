import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError, toApiError } from '../shared/api';
import { ErrorAlert } from '../shared/error-alert';
import { Icon } from '../shared/icon';
import { Auth } from './auth';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, ErrorAlert, Icon],
  template: `
    <div class="login">
      <section class="pitch">
        <div class="brand"><span class="logo">eF</span> eFacture <strong>PME</strong></div>
        <h1>La facturation électronique, sans erreur de conformité.</h1>
        <ul>
          <li>
            <app-icon name="invoice" />
            <span><strong>Factures Factur-X conformes</strong> : numérotation sans trou, mentions obligatoires vérifiées.</span>
          </li>
          <li>
            <app-icon name="send" />
            <span><strong>Suivi sur la plateforme agréée</strong> : dépôt, statuts, encaissement.</span>
          </li>
          <li>
            <app-icon name="sparkles" />
            <span><strong>Factures fournisseurs lues par IA</strong>, puis contrôlées avant validation.</span>
          </li>
        </ul>
        <p class="note">Projet de démonstration : la plateforme agréée est simulée.</p>
      </section>

      <section class="panel">
        <div class="form-card">
          <h2>Connexion</h2>
          <p class="muted">Utilisez un compte de démonstration ou vos identifiants.</p>

          <div class="demo-accounts">
            <button type="button" (click)="fill('gerant@demo.fr')">
              <strong>Gérant</strong>
              <span class="muted small">Accès complet</span>
            </button>
            <button type="button" (click)="fill('comptable@demo.fr')">
              <strong>Comptable</strong>
              <span class="muted small">Lecture, paiements, avoirs</span>
            </button>
          </div>

          <app-error-alert [error]="error()" />
          <form [formGroup]="form" (ngSubmit)="submit()">
            <label>E-mail <input formControlName="email" type="email" autocomplete="username" /></label>
            <label>Mot de passe <input formControlName="password" type="password" autocomplete="current-password" /></label>
            <button type="submit" class="primary" [disabled]="form.invalid || busy()">
              {{ busy() ? 'Connexion…' : 'Se connecter' }}
            </button>
          </form>
          <p class="muted small hint">Mot de passe des comptes de démonstration : <code>demo1234</code></p>
        </div>
      </section>
    </div>
  `,
  styles: `
    .login { display: grid; grid-template-columns: 1.1fr 1fr; min-height: 100vh; }
    .pitch {
      background: radial-gradient(circle at 20% 0%, #1e3a8a 0%, #0f172a 60%); color: #cbd5e1;
      padding: 48px 56px; display: flex; flex-direction: column; justify-content: center; gap: 24px;
    }
    .brand { display: flex; align-items: center; gap: 10px; color: #fff; font-size: 1.1rem; }
    .logo {
      width: 34px; height: 34px; border-radius: 8px; background: var(--primary); color: #fff;
      display: grid; place-items: center; font-weight: 700; font-size: 0.85rem;
    }
    .pitch h1 { color: #fff; font-size: 2rem; line-height: 1.25; max-width: 460px; }
    ul { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 16px; max-width: 460px; }
    li { display: flex; gap: 12px; align-items: flex-start; }
    li app-icon { color: #93b4ff; margin-top: 2px; }
    li strong { color: #fff; font-weight: 600; }
    .note { font-size: 0.85rem; color: #94a3b8; margin: 8px 0 0; }

    .panel { display: grid; place-items: center; padding: 32px 24px; }
    .form-card { width: 100%; max-width: 380px; }
    .form-card h2 { font-size: 1.4rem; margin: 0 0 4px; }
    .form-card > p { margin: 0 0 20px; }
    .demo-accounts { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 20px; }
    .demo-accounts button {
      flex-direction: column; align-items: flex-start; gap: 2px; padding: 12px; white-space: normal; text-align: left;
    }
    .demo-accounts button:hover { border-color: var(--primary); background: var(--primary-soft); }
    form { display: flex; flex-direction: column; gap: 14px; }
    form button { margin-top: 4px; padding: 10px; }
    .hint { margin-top: 16px; }

    @media (max-width: 860px) {
      .login { grid-template-columns: 1fr; }
      .pitch { padding: 32px 24px; }
      .pitch h1 { font-size: 1.5rem; }
    }
  `,
})
export class LoginPage {
  /** Where to go after login, set by the guard or the interceptor. */
  readonly redirect = input<string>();

  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly error = signal<ApiError | null>(null);
  protected readonly busy = signal(false);

  fill(email: string): void {
    this.form.setValue({ email, password: 'demo1234' });
  }

  submit(): void {
    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.redirect() || '/tableau-de-bord'),
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toApiError(e));
      },
    });
  }
}
