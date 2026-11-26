import { coverGradient, greetingFor, hueFromString, initialOf, PLAYLIST_NAME_FORBIDDEN } from './util';

describe('util', () => {
  it('greets according to the hour', () => {
    expect(greetingFor(8)).toBe('Buenos días');
    expect(greetingFor(14)).toBe('Buenas tardes');
    expect(greetingFor(21)).toBe('Buenas noches');
    expect(greetingFor(3)).toBe('Buenas noches');
  });

  it('derives a stable hue in 0-359', () => {
    expect(hueFromString('Rock')).toBe(hueFromString('Rock'));
    expect(hueFromString('Rock')).toBeGreaterThanOrEqual(0);
    expect(hueFromString('Rock')).toBeLessThan(360);
  });

  it('builds a gradient and an initial', () => {
    expect(coverGradient('Rock')).toContain('linear-gradient');
    expect(initialOf('  mi lista')).toBe('M');
    expect(initialOf('')).toBe('♪');
  });

  it('flags characters that break the playlist path', () => {
    expect(PLAYLIST_NAME_FORBIDDEN.test('a/b')).toBe(true);
    expect(PLAYLIST_NAME_FORBIDDEN.test('Mi lista 1')).toBe(false);
  });
});
