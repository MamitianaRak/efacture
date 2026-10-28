import { Component, Injectable, inject, signal } from '@angular/core';
import { Icon } from './icon';

export type ToastKind = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

/** Short confirmations of what just happened ("Facture émise"), shown in a corner and dismissed automatically. */
@Injectable({ providedIn: 'root' })
export class Toasts {
  readonly items = signal<Toast[]>([]);
  private nextId = 1;

  success(message: string): void {
    this.show('success', message);
  }

  error(message: string): void {
    this.show('error', message, 7000);
  }

  info(message: string): void {
    this.show('info', message);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((t) => t.id !== id));
  }

  private show(kind: ToastKind, message: string, durationMs = 4000): void {
    const id = this.nextId++;
    this.items.update((list) => [...list, { id, kind, message }]);
    setTimeout(() => this.dismiss(id), durationMs);
  }
}

@Component({
  selector: 'app-toasts',
  imports: [Icon],
  template: `
    <div class="toasts" aria-live="polite">
      @for (toast of toasts.items(); track toast.id) {
        <div class="toast" [class]="'toast ' + toast.kind" role="status">
          <app-icon [name]="toast.kind === 'success' ? 'check' : toast.kind === 'error' ? 'alert' : 'info'" />
          <span>{{ toast.message }}</span>
          <button type="button" class="ghost small" (click)="toasts.dismiss(toast.id)" aria-label="Fermer">
            <app-icon name="x" [size]="14" />
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts { position: fixed; right: 20px; bottom: 20px; z-index: 1000; display: flex; flex-direction: column; gap: 8px; }
    .toast {
      display: flex; align-items: center; gap: 10px; min-width: 280px; max-width: 420px;
      padding: 10px 8px 10px 14px; border-radius: var(--radius); background: var(--surface);
      border: 1px solid var(--border); box-shadow: var(--shadow-lg); animation: slide-in 0.2s ease-out;
    }
    .toast span { flex: 1; }
    .success app-icon { color: var(--success); }
    .error app-icon { color: var(--danger); }
    .info app-icon { color: var(--info); }
    @keyframes slide-in { from { transform: translateY(8px); opacity: 0; } }
    @media (max-width: 720px) { .toasts { left: 16px; right: 16px; } .toast { min-width: 0; } }
  `,
})
export class ToastHost {
  protected readonly toasts = inject(Toasts);
}
