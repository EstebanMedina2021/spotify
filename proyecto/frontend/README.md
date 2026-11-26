# Frontend (Angular 22)

Aplicación web de la API de playlists. La documentación completa (arquitectura, variables, seguridad) está en el [README del repositorio](../../README.md).

```bash
npm install
npx ng serve                  # http://localhost:4200  (necesita el backend en :8080)
npx ng test --watch=false     # pruebas (Vitest)
npx ng build                  # build de producción en dist/
```

- `proxy.conf.json` reenvía `/api/*` al backend en `:8080`, de modo que el navegador usa un solo origen (cookies y CSRF sin CORS).
- La URL base de la API se toma de `src/environments/`.
- Estructura de `src/app/`: `core/` (servicios, interceptores, guard, modelos), `layout/` (sidebar, barra superior, barra del reproductor), `shared/` (componentes reutilizables sin llamadas HTTP) y `pages/` (login, inicio, lista, búsqueda, crear lista).
- El reproductor no reproduce audio: la API no tiene fuente de audio, por eso sus controles están deshabilitados.
