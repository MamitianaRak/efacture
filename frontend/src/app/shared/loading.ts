import { HttpInterceptorFn } from '@angular/common/http';
import { Component, Injectable, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

/** Counts the API calls in flight, so the shell can show that something is happening. */
@Injectable({ providedIn: 'root' })
export class Loading {
  private readonly pending = signal(0);
  readonly active = computed(() => this.pending() > 0);

  start(): void {
    this.pending.update((n) => n + 1);
  }

  stop(): void {
    this.pending.update((n) => Math.max(0, n - 1));
  }
}

export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  const loading = inject(Loading);
  loading.start();
  return next(request).pipe(finalize(() => loading.stop()));
};

/** Thin bar at the top of the window. Shown after a short delay, so fast calls do not make it flicker. */
@Component({
  selector: 'app-progress-bar',
  template: `
    @if (loading.active()) {
      <div class="progress" role="progressbar" aria-label="Chargement en cours"><span></span></div>
    }
  `,
  styles: `
    .progress {
      position: fixed; top: 0; left: 0; right: 0; height: 3px; z-index: 1000; overflow: hidden;
      background: var(--primary-soft); opacity: 0; animation: appear 0s linear 250ms forwards;
    }
    span {
      position: absolute; top: 0; bottom: 0; width: 40%; background: var(--primary);
      animation: slide 1.1s ease-in-out infinite;
    }
    @keyframes appear { to { opacity: 1; } }
    @keyframes slide { from { left: -40%; } to { left: 100%; } }
  `,
})
export class ProgressBar {
  protected readonly loading = inject(Loading);
}
