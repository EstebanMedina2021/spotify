/** Greeting that depends on the hour of the day (0-23). */
export function greetingFor(hour: number): string {
  if (hour >= 6 && hour < 12) {
    return 'Buenos días';
  }
  if (hour >= 12 && hour < 19) {
    return 'Buenas tardes';
  }
  return 'Buenas noches';
}

/** Stable hue (0-359) derived from a string, so the same name always gets the same color. */
export function hueFromString(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = (hash * 31 + value.charCodeAt(i)) >>> 0;
  }
  return hash % 360;
}

/** Gradient used as a cover when there is no image (the API stores no artwork). */
export function coverGradient(seed: string): string {
  const hue = hueFromString(seed);
  return `linear-gradient(135deg, hsl(${hue} 60% 42%), hsl(${(hue + 40) % 360} 55% 24%))`;
}

/** Darker color for the header backdrop of a playlist page. */
export function headerColor(seed: string): string {
  return `hsl(${hueFromString(seed)} 45% 28%)`;
}

export function initialOf(value: string): string {
  const trimmed = value.trim();
  return trimmed ? trimmed.charAt(0).toUpperCase() : '♪';
}

/** Characters that break the `/lists/{name}` path (encoded slashes are rejected by Tomcat). */
export const PLAYLIST_NAME_FORBIDDEN = /[\/\\?#%]/;

// eslint-disable-next-line no-control-regex
export const CONTROL_CHARS = /[\u0000-\u001F\u007F]/;
