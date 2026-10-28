import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Auth } from '../auth/auth';
import { Api, ApiError, toApiError } from '../shared/api';
import { Toasts } from '../shared/toast';
import { Invoice, PLATFORM_STATUS_LABELS, Submission } from '../shared/api.models';
import { ErrorAlert } from '../shared/error-alert';
import { PlatformBadge } from './platform-badge';

const POLL_MS = 3000;

/** Submission of an issued invoice to the (simulated) registered platform and its status history. */
@Component({
  selector: 'app-platform-panel',
  imports: [DatePipe, ErrorAlert, PlatformBadge],
  template: `
    <section class="card">
      <div class="page-header">
        <h2>Plateforme agréée <span class="badge">Simulée</span></h2>
        @if (submission(); as s) {
          <app-platform-badge [status]="s.status" />
        }
      </div>

      <app-error-alert [error]="error()" />

      @if (submission(); as s) {
        @if (s.status === 'EN_COURS_ENVOI' && s.deliveryAttempts > 0) {
          <p class="muted">
            Plateforme injoignable pour l’instant ({{ s.deliveryAttempts }} tentative(s), dernière erreur :
            {{ s.lastDeliveryError }}). Le dépôt est conservé et sera renvoyé automatiquement.
          </p>
        } @else if (s.status === 'ECHEC_ENVOI') {
          <p class="muted">La plateforme est restée injoignable après plusieurs tentatives : déposez à nouveau.</p>
        } @else if (s.status === 'REJETEE') {
          <p class="muted">Rejet technique : corrigez si besoin, puis déposez à nouveau.</p>
        } @else if (s.status === 'REFUSEE') {
          <p class="muted">Le client a refusé la facture : émettez un avoir pour l’annuler.</p>
        } @else if (!s.finalStatus) {
          <p class="muted">Traitement en cours sur la plateforme…</p>
        }

        <ol class="timeline">
          @for (event of s.events; track $index) {
            <li>
              <strong>{{ labels[event.status] }}</strong>
              <span class="muted">{{ event.occurredAt | date: 'dd/MM/yyyy HH:mm:ss' }}</span>
              <span class="muted">· {{ event.source === 'SELLER' ? 'vous' : 'plateforme' }}</span>
              @if (event.reason) {
                <div class="reason">{{ event.reason }}</div>
              }
            </li>
          }
        </ol>
      } @else if (loaded()) {
        <p class="muted">Pas encore déposée. Le dépôt envoie le Factur-X à la plateforme, qui le transmet au client.</p>
      }

      <div class="actions">
        @if (canSubmit()) {
          <button type="button" class="primary" (click)="action.set('submit'); submit()" [disabled]="busy()"
                  [class.loading]="busy() && action() === 'submit'">
            @if (busy() && action() === 'submit') {
              Dépôt en cours…
            } @else {
              {{ submission() ? 'Déposer à nouveau' : 'Déposer sur la plateforme' }}
            }
          </button>
        }
        @if (canMarkPaid()) {
          <button type="button" (click)="action.set('paid'); markPaid()" [disabled]="busy()"
                  [class.loading]="busy() && action() === 'paid'">Marquer comme encaissée</button>
        }
      </div>
    </section>
  `,
  styles: `
    h2 { font-size: 1.1rem; margin: 0; }
    .timeline { list-style: none; padding: 0; margin: 12px 0; border-left: 2px solid var(--border); }
    .timeline li { padding: 4px 0 8px 14px; position: relative; }
    .timeline li::before {
      content: ''; position: absolute; left: -6px; top: 10px;
      width: 10px; height: 10px; border-radius: 50%; background: var(--primary);
    }
    .reason { color: var(--danger); font-size: 0.9rem; }
  `,
})
export class PlatformPanel {
  readonly invoice = input.required<Invoice>();

  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  private readonly isManager = inject(Auth).isManager;

  protected readonly labels = PLATFORM_STATUS_LABELS;
  protected readonly submission = signal<Submission | null>(null);
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly action = signal<'submit' | 'paid' | null>(null);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly canSubmit = computed(() => {
    const s = this.submission();
    return this.isManager() && this.loaded() && (!s || s.status === 'REJETEE' || s.status === 'ECHEC_ENVOI');
  });
  protected readonly canMarkPaid = computed(() => {
    const status = this.submission()?.status;
    return status === 'MISE_A_DISPOSITION' || status === 'APPROUVEE';
  });

  constructor() {
    // Follow the platform's progress until the status is final.
    const timer = setInterval(() => {
      if (this.submission() && !this.submission()!.finalStatus) {
        this.refresh();
      }
    }, POLL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));

    // Reload when the panel is reused for another invoice.
    effect(() => {
      this.invoice().id;
      untracked(() => {
        this.submission.set(null);
        this.loaded.set(false);
        this.refresh();
      });
    });
  }

  submit(): void {
    this.act(this.api.submit(this.invoice().id), 'Dépôt enregistré : envoi à la plateforme en cours');
  }

  markPaid(): void {
    this.act(this.api.markPaid(this.invoice().id), 'Encaissement enregistré et transmis à la plateforme');
  }

  private refresh(): void {
    this.api.getSubmission(this.invoice().id).subscribe({
      next: (submission) => {
        this.submission.set(submission);
        this.loaded.set(true);
      },
      error: (e: unknown) => {
        this.loaded.set(true);
        // 404: never submitted, which is a normal state.
        if (!(e instanceof HttpErrorResponse && e.status === 404)) {
          this.error.set(toApiError(e));
        }
      },
    });
  }

  private act(call: ReturnType<Api['submit']>, successMessage: string): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (submission) => {
        this.submission.set(submission);
        this.busy.set(false);
        this.toasts.success(successMessage);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toApiError(e));
      },
    });
  }
}
