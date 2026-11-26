import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlaylistService } from './playlist.service';
import { RecommendationResponse } from './models';

const recs = (fallback: boolean): RecommendationResponse => ({
  playlistNombre: 'Mi lista',
  recomendaciones: [{ titulo: 'Under Pressure', artista: 'Queen' }],
  fuente: fallback ? 'FALLBACK' : 'GEMINI',
  promptVersion: 'v1',
  fallback,
  motivoFallback: fallback ? 'TIMEOUT' : null,
  mensaje: null,
});

describe('PlaylistService', () => {
  let service: PlaylistService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PlaylistService);
    http = TestBed.inject(HttpTestingController);
  });

  it('encodes the playlist name in the URL', () => {
    service.get('Rock & Roll #1').subscribe();

    http.expectOne('/api/lists/Rock%20%26%20Roll%20%231').flush({});
  });

  it('refresh() fills the signal and marks it loaded', () => {
    service.refresh();
    http.expectOne('/api/lists').flush([{ id: '1', nombre: 'A', descripcion: null, numeroCanciones: 2 }]);

    expect(service.playlists()).toHaveLength(1);
    expect(service.isLoaded()).toBe(true);
    expect(service.isLoading()).toBe(false);
  });

  it('caches successful recommendations (second call makes no request)', () => {
    service.recommendations('Mi lista').subscribe();
    http.expectOne('/api/lists/Mi%20lista/recommendations').flush(recs(false));

    let result: RecommendationResponse | undefined;
    service.recommendations('mi LISTA').subscribe((r) => (result = r));

    http.expectNone('/api/lists/mi%20LISTA/recommendations');
    expect(result?.fuente).toBe('GEMINI');
  });

  it('does not cache fallback recommendations', () => {
    service.recommendations('Mi lista').subscribe();
    http.expectOne('/api/lists/Mi%20lista/recommendations').flush(recs(true));

    service.recommendations('Mi lista').subscribe();
    http.expectOne('/api/lists/Mi%20lista/recommendations').flush(recs(false));
  });

  it('create() posts the request and refreshes the list', () => {
    service.create({ nombre: 'Nueva', canciones: [] }).subscribe();

    const post = http.expectOne('/api/lists');
    expect(post.request.method).toBe('POST');
    post.flush({ id: '1', nombre: 'Nueva' });
    http.expectOne('/api/lists').flush([]);
  });
});
