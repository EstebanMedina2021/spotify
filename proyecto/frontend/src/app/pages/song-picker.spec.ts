import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Cancion } from '../core/models';
import { SongPicker } from './song-picker';

describe('SongPicker', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [SongPicker],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  function create() {
    const fixture = TestBed.createComponent(SongPicker);
    const emitted: Cancion[] = [];
    fixture.componentInstance.songSelected.subscribe((song) => emitted.push(song));
    fixture.detectChanges();
    // the picker loads the genre suggestions once
    http.expectOne('/api/spotify/genres').flush({ generos: ['rock', 'pop'], fallback: true, motivoFallback: 'ENDPOINT_UNAVAILABLE' });
    return { fixture, el: fixture.nativeElement as HTMLElement, emitted };
  }

  function type(el: HTMLElement, selector: string, value: string): void {
    const input = el.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('searches Spotify only after the debounce and emits the chosen track', async () => {
    const { fixture, el, emitted } = create();

    type(el, '#spotify-q', 'queen');
    await vi.advanceTimersByTimeAsync(200);
    http.expectNone((req) => req.url === '/api/spotify/search'); // still within the debounce window

    await vi.advanceTimersByTimeAsync(200);
    const request = http.expectOne((req) => req.url === '/api/spotify/search');
    expect(request.request.params.get('q')).toBe('queen');
    request.flush({ resultados: [{ titulo: 'Bohemian Rhapsody', artista: 'Queen', album: 'A Night At The Opera', spotifyId: 'x1' }] });
    fixture.detectChanges();

    (el.querySelector('.results .add') as HTMLButtonElement).click();
    expect(emitted).toEqual([{ titulo: 'Bohemian Rhapsody', artista: 'Queen', album: 'A Night At The Opera' }]);
  });

  it('does not search for fewer than 2 characters', async () => {
    const { el } = create();

    type(el, '#spotify-q', 'q');
    await vi.advanceTimersByTimeAsync(500);

    http.expectNone((req) => req.url === '/api/spotify/search');
  });

  it('keeps working after a failed search (stream survives the error)', async () => {
    const { fixture, el } = create();

    type(el, '#spotify-q', 'queen');
    await vi.advanceTimersByTimeAsync(400);
    http.expectOne((req) => req.url === '/api/spotify/search').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(el.textContent).toContain('No se pudo buscar en Spotify');

    type(el, '#spotify-q', 'abba');
    await vi.advanceTimersByTimeAsync(400);
    const retry = http.expectOne((req) => req.url === '/api/spotify/search');
    expect(retry.request.params.get('q')).toBe('abba');
    retry.flush({ resultados: [] });
  });

  it('emits a manual song and clears the form; rejects an incomplete one', () => {
    const { fixture, el, emitted } = create();
    const add = () => (el.querySelector('.manual-grid > button') as HTMLButtonElement);

    type(el, '#m-titulo', 'Only title');
    fixture.detectChanges();
    expect(add().disabled).toBe(true);

    type(el, '#m-artista', 'Some Artist');
    type(el, '#m-genero', 'rock');
    fixture.detectChanges();
    add().click();

    expect(emitted).toHaveLength(1);
    expect(emitted[0]).toMatchObject({ titulo: 'Only title', artista: 'Some Artist', genero: 'rock' });
    expect((el.querySelector('#m-titulo') as HTMLInputElement).value).toBe('');
  });

  it('pressing Enter in the picker does not submit the surrounding form', () => {
    const { el } = create();
    const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true, bubbles: true });

    el.querySelector('#spotify-q')!.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
  });
});
