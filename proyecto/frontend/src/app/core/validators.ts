import { AbstractControl, ValidationErrors } from '@angular/forms';
import { CONTROL_CHARS, PLAYLIST_NAME_FORBIDDEN } from './util';

/** Rejects values that are empty after trimming (the backend trims and rejects blank names). */
export function notBlank(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return typeof value === 'string' && value.length > 0 && value.trim().length === 0
    ? { blank: true }
    : null;
}

/** Control characters are rejected by the backend; fail early with a clear message. */
export function noControlChars(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return typeof value === 'string' && CONTROL_CHARS.test(value) ? { controlChars: true } : null;
}

/** `/ \ ? # %` break the /lists/{name} path. */
export function playlistNameChars(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return typeof value === 'string' && PLAYLIST_NAME_FORBIDDEN.test(value) ? { forbiddenChars: true } : null;
}
