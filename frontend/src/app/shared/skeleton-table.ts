import { Component, computed, input } from '@angular/core';

/** Placeholder table shown while a list loads, so the page keeps its shape. */
@Component({
  selector: 'app-skeleton-table',
  template: `
    <table class="card" aria-busy="true" aria-label="Chargement">
      <tbody>
        @for (row of rows; track row) {
          <tr>
            @for (col of cols(); track col) {
              <td><span class="skeleton" [style.width.%]="widths[(row + col) % widths.length]"></span></td>
            }
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class SkeletonTable {
  readonly columns = input(5);

  protected readonly rows = [0, 1, 2, 3];
  protected readonly widths = [70, 45, 60, 35, 55];
  protected readonly cols = computed(() => Array.from({ length: this.columns() }, (_, i) => i));
}
