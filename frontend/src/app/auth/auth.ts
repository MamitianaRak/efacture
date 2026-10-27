import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type Role = 'MANAGER' | 'ACCOUNTANT';

export interface CurrentUser {
  email: string;
  displayName: string;
  role: Role;
}

interface Session {
  token: string;
  expiresAt: string;
  user: CurrentUser;
}

const STORAGE_KEY = 'efacture.session';

/**
 * Holds the JWT. Kept in sessionStorage so a page refresh keeps the user logged in,
 * while closing the tab ends the session.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isManager = computed(() => this.user()?.role === 'MANAGER');

  token(): string | null {
    const session = this.session();
    if (session && new Date(session.expiresAt) <= new Date()) {
      this.logout();
      return null;
    }
    return session?.token ?? null;
  }

  login(email: string, password: string): Observable<Session> {
    return this.http.post<Session>('/api/auth/login', { email, password }).pipe(
      tap((session) => {
        this.session.set(session);
        try {
          sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        } catch {
          // Storage unavailable (private mode): the session lives in memory only.
        }
      }),
    );
  }

  logout(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nothing to clean.
    }
  }
}

function restore(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const session = raw ? (JSON.parse(raw) as Session) : null;
    return session && new Date(session.expiresAt) > new Date() ? session : null;
  } catch {
    return null;
  }
}
