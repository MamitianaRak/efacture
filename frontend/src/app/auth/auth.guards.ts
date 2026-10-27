import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

export const loggedIn: CanActivateFn = (_route, state) => {
  const auth = inject(Auth);
  return auth.token()
    ? true
    : inject(Router).createUrlTree(['/connexion'], { queryParams: { redirect: state.url } });
};

/** Screens that only the manager can use. The API enforces the same rule; this only avoids a dead end. */
export const managerOnly: CanActivateFn = () => {
  return inject(Auth).isManager() ? true : inject(Router).createUrlTree(['/factures']);
};
