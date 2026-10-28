import { Component, ElementRef, Injectable, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface DialogRequest {
  title: string;
  message: string;
  confirmLabel: string;
  danger: boolean;
  /** When set, the dialog asks for a text (e.g. a rejection reason) and requires it. */
  inputLabel?: string;
  resolve: (value: string | null) => void;
}

/**
 * Confirmation and short-input dialogs, replacing the browser's confirm() and prompt(): consistent with the app,
 * keyboard accessible (native <dialog>: focus trap, Escape to cancel).
 */
@Injectable({ providedIn: 'root' })
export class Dialogs {
  readonly current = signal<DialogRequest | null>(null);

  confirm(title: string, message: string, options: { confirmLabel?: string; danger?: boolean } = {}): Promise<boolean> {
    return new Promise((resolve) =>
      this.current.set({
        title,
        message,
        confirmLabel: options.confirmLabel ?? 'Confirmer',
        danger: options.danger ?? false,
        resolve: (value) => resolve(value !== null),
      }),
    );
  }

  /** Resolves with the text entered, or null if cancelled. */
  prompt(title: string, message: string, inputLabel: string, confirmLabel = 'Valider'): Promise<string | null> {
    return new Promise((resolve) =>
      this.current.set({ title, message, confirmLabel, danger: true, inputLabel, resolve }),
    );
  }
}

@Component({
  selector: 'app-dialog-host',
  imports: [FormsModule],
  template: `
    <dialog #dialog (cancel)="close(null)">
      @if (dialogs.current(); as d) {
        <form method="dialog" (submit)="$event.preventDefault(); submit()">
          <h2>{{ d.title }}</h2>
          <p class="muted">{{ d.message }}</p>
          @if (d.inputLabel) {
            <label>
              {{ d.inputLabel }}
              <textarea rows="3" [(ngModel)]="text" name="text" required></textarea>
            </label>
          }
          <div class="actions">
            <button type="button" (click)="close(null)">Annuler</button>
            <button type="submit" [class.primary]="!d.danger" [class.danger-solid]="d.danger"
                    [disabled]="!!d.inputLabel && !text.trim()">
              {{ d.confirmLabel }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styles: `
    dialog {
      border: none; border-radius: var(--radius); padding: 24px; width: min(440px, calc(100vw - 32px));
      box-shadow: var(--shadow-lg);
    }
    dialog::backdrop { background: rgba(17, 24, 39, 0.45); }
    p { margin: 0 0 16px; }
    label { margin-bottom: 16px; }
  `,
})
export class DialogHost {
  protected readonly dialogs = inject(Dialogs);
  protected text = '';
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.dialogs.current()) {
        this.text = '';
        if (!element.open) {
          element.showModal();
        }
      } else if (element.open) {
        element.close();
      }
    });
  }

  submit(): void {
    const request = this.dialogs.current();
    if (request) {
      this.close(request.inputLabel ? this.text.trim() : '');
    }
  }

  close(value: string | null): void {
    const request = this.dialogs.current();
    this.dialogs.current.set(null);
    request?.resolve(value);
  }
}
