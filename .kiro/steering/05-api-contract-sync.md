---
inclusion: fileMatch
fileMatchPattern: "**/services/**,**/models/**,**/interfaces/**,**/controller/**,**/dto/**"
---

# Skill: Sincronización de Contratos API & Audio Streaming

Cuando modifiques o conectes endpoints entre `proyecto/frontend` y `proyecto/backend`, verifica obligatoriamente:

1. **Consistencia DTO <-> Interface:** Asegúrate de que los campos JSON serializados por Jackson en Spring Boot coincidan exactamente (nombres, nulabilidad y tipos) con las interfaces TypeScript en Angular.
2. **Unidades de Tiempo:** Estandariza si la duración de las pistas viaja en **segundos** o **milisegundos** y nómbralo explícitamente (`durationSeconds` o `durationMs`).
3. **Soporte de Audio Seeking:** Al conectar endpoints de reproducción de audio, verifica que Spring Boot responda con `206 Partial Content` y cabeceras `Accept-Ranges: bytes` y `Content-Range` para permitir adelantar/retroceder la canción en el navegador.
