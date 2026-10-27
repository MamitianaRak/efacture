import { Component, input } from '@angular/core';
import { ApiError } from './api';

@Component({
  selector: 'app-error-alert',
  template: `
    @if (error(); as e) {
      <div class="alert" role="alert">
        <strong>{{ e.message }}</strong>
        @if (e.details.length) {
          <ul>
            @for (detail of e.details; track detail) {
              <li>{{ detail }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
})
export class ErrorAlert {
  readonly error = input<ApiError | null>(null);
}
