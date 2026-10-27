import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('shows the navigation and the demo disclaimer', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    const links = [...page.querySelectorAll('nav a')].map((a) => a.textContent?.trim());
    expect(links).toEqual(['Factures', 'Clients', 'Mon entreprise']);
    expect(page.querySelector('footer')?.textContent).toContain('démonstration');
  });
});
