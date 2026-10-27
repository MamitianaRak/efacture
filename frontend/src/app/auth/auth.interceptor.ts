import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Auth } from './auth';

/** Adds the bearer token to API calls and sends the user back to the login page when it is rejected. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(Auth);
  const router = inject(Router);
  const token = auth.token();
  const isLogin = request.url.endsWith('/api/auth/login');

  const authorized =
    token && !isLogin ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isLogin) {
        auth.logout();
        router.navigate(['/connexion'], { queryParams: { redirect: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
