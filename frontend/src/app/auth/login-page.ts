import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError, toApiError } from '../shared/api';
import { ErrorAlert } from '../shared/error-alert';
import { Auth } from './auth';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, ErrorAlert],
  template: `
    <div class="login">
      <h1>Connexion</h1>
      <app-error-alert [error]="error()" />
      <form class="card" [formGroup]="form" (ngSubmit)="submit()">
        <label>E-mail <input formControlName="email" type="email" autocomplete="username" /></label>
        <label>Mot de passe <input formControlName="password" type="password" autocomplete="current-password" /></label>
        <button type="submit" class="primary" [disabled]="form.invalid || busy()">Se connecter</button>
      </form>
      <div class="card muted demo">
        <strong>Comptes de démonstration</strong> (mot de passe <code>demo1234</code>)
        <div class="demo-accounts">
          <button type="button" (click)="fill('gerant@demo.fr')">Gérant</button>
          <button type="button" (click)="fill('comptable@demo.fr')">Comptable</button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .login { max-width: 400px; margin: 40px auto; }
    form { display: flex; flex-direction: column; gap: 16px; }
    .demo { font-size: 0.9rem; }
    .demo-accounts { display: flex; gap: 8px; margin-top: 8px; }
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
      next: () => this.router.navigateByUrl(this.redirect() || '/factures'),
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toApiError(e));
      },
    });
  }
}
