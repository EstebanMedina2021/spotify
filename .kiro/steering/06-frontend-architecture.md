---
inclusion: fileMatch
fileMatchPattern: "proyecto/frontend/**"
---

# Arquitectura Frontend (Angular - Spotify Demo)

Cuando crees o modifiques código dentro de `proyecto/frontend/`, sigue estrictamente esta arquitectura modular:

## 1. Estructura de Carpetas en `src/app/`
- `core/`: Servicios singleton (`providedIn: 'root'`), interceptors HTTP, guards de rutas y el servicio global del reproductor (`AudioPlayerService`).
- `shared/`: Componentes UI reutilizables y puros (sin llamadas HTTP), pipes personalizados (`DurationPipe` para `mm:ss`) y directivas.
- `features/`: Módulos/rutas funcionales aisladas (`home/`, `search/`, `playlist/`, `library/`, `auth/`). Una feature nunca debe importar componentes internos de otra feature.
- `models/`: Interfaces TypeScript (`Track`, `Playlist`, `Album`, `Artist`, `PlayerState`).

## 2. Arquitectura del Reproductor de Audio (Estado Global)
- **Single Source of Truth:** El elemento HTML5 `<audio>` debe vivir únicamente dentro de `AudioPlayerService` en `core/services/`.
- **Reactividad con Signals:** Expón el estado del reproductor mediante Angular Signals (`currentTrack`, `isPlaying`, `currentTime`, `duration`, `volume`, `queue`) para evitar re-renderizados innecesarios en toda la aplicación.
- **Persistencia Visual:** El layout principal debe mantener fija la barra lateral (`Sidebar`) y la barra inferior (`PlayerBar`), renderizando las vistas únicamente dentro de `<router-outlet>`.

## 3. Estándar Visual (Spotify Dark UI)
- Paleta obligatoria: Fondo principal `#121212`, superficies/tarjetas `#181818`, hover `#282828`, barra inferior `#000000`, acento verde `#1DB954`, texto primario `#FFFFFF`, texto secundario `#B3B3B3`.
- Usa `@if` y `@for` (control flow nativo moderno de Angular) en lugar de `*ngIf` y `*ngFor`.
