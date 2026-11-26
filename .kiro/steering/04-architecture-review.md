---
inclusion: manual
---

# Skill: Revisión de Arquitectura (Angular & Spring Boot)

Evalúa la estructura del proyecto, el acoplamiento entre módulos y los flujos de datos de extremo a extremo.

## Criterios de Evaluación
1. **Arquitectura Angular:** Evalúa la separación entre `core/` (servicios singleton, interceptors, audio player), `shared/` (componentes reutilizables) y `features/` (home, search, playlist, library). Verifica si el estado del reproductor persiste al navegar entre rutas.
2. **Escalabilidad del Streaming (Spring Boot):** Analiza cómo se entregan los archivos de audio. Si el backend carga el archivo completo en un `byte[]` en memoria en lugar de usar `ResourceRegion` / `StreamingResponseBody` con soporte para `Range` (HTTP 206), márcalo como cuello de botella crítico.
3. **Proxy y Entornos:** Verifica que el frontend consuma el backend mediante `proxy.conf.json` en desarrollo sin URLs hardcodeadas.

## Entregable
1. **Fortalezas actuales**
2. **Cuellos de botella y acoplamientos detectados**
3. **Diagrama ASCII del flujo actual vs. recomendado**
4. **Plan de refactorización incremental (en 3 pasos)**
