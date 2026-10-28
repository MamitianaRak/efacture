import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
  });

  it('hides the navigation until the user is logged in', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('nav')).toBeNull();
    expect(page.querySelector('.sidebar')).toBeNull();
    expect(page.querySelector('router-outlet')).not.toBeNull();
  });

  it('shows the navigation and the demo banner once logged in', async () => {
    sessionStorage.setItem(
      'efacture.session',
      JSON.stringify({ token: 't', expiresAt: '2999-01-01T00:00:00Z', user: { email: 'gerant@demo.fr', displayName: 'Gérant', role: 'MANAGER' } }),
    );
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('nav')?.textContent).toContain('Factures');
    expect(page.querySelector('.demo-banner')?.textContent).toContain('plateforme agréée est simulée');
  });
});
