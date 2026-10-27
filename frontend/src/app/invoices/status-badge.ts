import { Component, computed, input } from '@angular/core';
import { InvoiceStatus } from '../shared/api.models';

const LABELS: Record<InvoiceStatus, string> = {
  DRAFT: 'Brouillon',
  ISSUED: 'Émise',
};

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class.success]="status() === 'ISSUED'">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<InvoiceStatus>();
  protected readonly label = computed(() => LABELS[this.status()]);
}
