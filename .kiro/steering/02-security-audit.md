---
inclusion: manual
---

# Skill: Auditoría de Seguridad (Angular & Spring Boot)

Analiza el código buscando vulnerabilidades reales. Señala archivo, línea exacta, vector de ataque y parche.

## Vectores Críticos a Auditar

### Frontend (proyecto/frontend)
- **Tokens y Autenticación:** Audita el `HttpInterceptor` de autenticación y cómo se almacenan/renuevan los JWT (`localStorage` vs cookies `HttpOnly`).
- **Inyección XSS:** Revisa cualquier uso de `bypassSecurityTrustHtml`, `bypassSecurityTrustUrl` o `[innerHTML]` al renderizar letras o metadatos.
- **Exposición de Datos:** Busca credenciales o URLs internas hardcodeadas fuera de `environment.ts`.

### Backend (proyecto/backend)
- **Control de Acceso (IDOR / BOLA):** *Crítico en apps tipo Spotify.* Verifica que un usuario no pueda editar o borrar playlists de otro usuario manipulando el `playlistId` en el endpoint.
- **Path Traversal & Streaming:** Si el backend lee archivos de audio desde `data/` o el sistema de archivos, verifica que se normalice y valide la ruta (`Path.normalize()`) para evitar acceso a archivos fuera del directorio permitido.
- **CORS y Spring Security:** Revisa que `CorsConfiguration` no use `*` junto con `allowCredentials(true)` y que los endpoints privados estén protegidos.

## Formato de Respuesta
Indica: **Severidad (Crítica/Alta/Media)**, **Vector de Ataque**, **Archivo/Línea** y **Código Corregido**.
