import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route parameters (":id") are bound straight to component inputs.
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch()),
    // Amounts and dates in French format: 1 234,50 €, 27/10/2026.
    { provide: LOCALE_ID, useValue: 'fr-FR' },
  ],
};
