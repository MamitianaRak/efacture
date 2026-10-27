import { Component, computed, input } from '@angular/core';
import { PLATFORM_STATUS_LABELS, PlatformStatus } from '../shared/api.models';

const TONES: Record<PlatformStatus, string> = {
  EN_COURS_ENVOI: 'warning',
  ECHEC_ENVOI: 'danger',
  DEPOSEE: '',
  MISE_A_DISPOSITION: '',
  APPROUVEE: 'success',
  ENCAISSEE: 'success',
  REJETEE: 'danger',
  REFUSEE: 'danger',
};

@Component({
  selector: 'app-platform-badge',
  template: `<span class="badge" [class]="tone()">{{ label() }}</span>`,
})
export class PlatformBadge {
  readonly status = input.required<PlatformStatus>();
  protected readonly label = computed(() => PLATFORM_STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => `badge ${TONES[this.status()]}`);
}
