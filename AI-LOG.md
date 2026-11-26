# AI-LOG — Bitácora de uso de Inteligencia Artificial

> **Entregable obligatorio.** Documenta de forma honesta y concreta cómo se usó la IA durante el desarrollo de esta prueba técnica. Prompts pegados tal cual. Decisiones aceptadas, rechazadas y corregidas con justificación.

---

## 1. Metadata

| Campo | Valor |
|---|---|
| **Candidato** | Esteban Medina Palacio |
| **Nivel** | ☐ Staff I/II · ☐ Staff III · ☒ Senior |
| **Fecha** | 26-11-2026 |
| **Asistentes de IA usados** | Claude (Anthropic, chat — diseño y auditoría) · Grok (implementación de las Fases 0–4.3 en modo copia-pega, ver `grok/PROMPT-ARRANQUE-GROK.md`; sustituyó a Claude Code, que no se pudo usar: sin plan de pago no se integraba con el IDE, y el chat de Claude se quedó sin tokens) · Kiro (ejecución, revisión final con skills y guía de auditoría, correcciones, Fase 5, README y Postman) |

**Nota metodológica:** este log se construye de forma **cooperativa con varias IAs**, pero las secciones "Explicación con mis palabras" y "Reflexión final" son **exclusivamente humanas**. La IA puede redactar borradores; la honestidad y el criterio son del candidato.

**Cambio de workflow documentado:** durante el diseño, el rol de las IAs cambió. Inicialmente se planteó que Kiro implementara y Claude revisara. Se cambió a: **un asistente generador implementa, el humano revisa en cada fase, Kiro audita al final de forma independiente**. El generador planeado era Claude Code; en la práctica fue **Grok** (ver la nota de §2.5). Esta decisión se justifica en §2.5.

---

## 2. Fase de diseño y planificación (previa a la implementación)

### 2.1. Interacción 1 — Análisis inicial de los PDFs

**Asistente:** Claude (Anthropic, chat)

**Prompt usado (textual):**

> "Necesito que analices estos archivos, haremos una prueba técnica, simula que eres un Senior AI Engineer, sé riguroso en la comprensión de los documentos, no supongas, si tienes preguntas, hazmelas saber"

**Respuesta relevante de la IA:**

- Análisis riguroso de los 4 PDFs (`SENIOR`, `PLANTILLA-AI-LOG`, `Teorica-Senior`, `Modulo-IA-Senior`).
- Identificación de **17 ambigüedades** agrupadas por bloqueante / datos / módulo IA / entregables. Entre ellas:
  - Versión de Java/Spring no especificada.
  - Flujo JWT no definido.
  - Esquema de canción truncado en el PDF (página 2 con `album` suelto).
  - Spotify mencionado en un PDF pero no en otro.
  - Alcance exacto no claro (¿práctica completa o solo módulo IA?).
- Recomendación explícita de **no asumir** Spring Boot, H2, JWT ni `album` sin confirmación del candidato.

**¿Qué acepté y por qué?**

- El enfoque de "no asumir, preguntar antes de codificar". Coherente con el nivel Senior esperado: un evaluador valora criterio, no velocidad.
- La lista de 17 preguntas: cubrió huecos que el PDF no resolvía y me obligó a definir el alcance con precisión.

**¿Qué rechacé o corregí y por qué?**

- Nada en esta interacción. La IA respetó la instrucción de no suponer.

**Explicación con mis palabras:**

Le pedí a la IA que se comportara como Senior y no asumiera nada. Cumplió: en lugar de generar código de inmediato, primero mapeó el problema y me hizo definir el alcance. Eso es exactamente lo que un Senior hace con un requerimiento incompleto.

---

### 2.2. Interacción 2 — Definición de stack y arquitectura

**Asistente:** Claude (Anthropic, chat)

**Prompt usado (resumen de mis respuestas a las 17 preguntas):**

> 1. Haremos el módulo IA y la práctica completa; lo teórico lo haré aparte.
> 2. No hay versión más amplia; usaremos JWT, H2, Spotify y frontend.
> 3. Spring Boot, Visual Studio Code, código listo para agregar.
> 4. Versiones más actualizadas, Lombok permitido, mejoras bienvenidas.
> 5. Sin roles; indaga sobre la solución apropiada.
> 6. Info de Spotify en la Web API oficial.
> 7. Nombre duplicado devuelve error.
> 8. Validar vacío/blank, máx 50 caracteres, sanitización en back y front.
> 9. Lista vacía devuelve 200 con aviso al usuario.
> 10. Sigamos tu recomendación de proveedor IA.
> 11. Propón recomendación sobre formato de respuesta.
> 12. Fallback según tipo de fallo.
> 13. Separar responsabilidades, mantenible y extensible.
> 14. Abierto a recomendaciones de tests.
> 15. AI-LOG en español, registrar esta conversación.
> 16. En vez de `AI_API_KEY`, usar nombre del proveedor como prefijo (`GEMINI_AI_API_KEY`, `OLLAMA_AI_API_KEY`).
> 17. Sin sacrificar calidad ni seguridad.

**Respuesta relevante de la IA:**

- **Stack propuesto:** Java 25 LTS, Spring Boot 4.2.0, Spring Security 7, Spring AI 2.0.x, Resilience4j, Bucket4j, H2 2.5.252, Angular 22.
- **Arquitectura en capas** con `AiRecommendationClient` como interfaz y `FallbackAiClient` como decorator.
- **Seguridad:** JWT HS256, aislamiento por `ownerId` (BOLA/IDOR), rate limiting, headers de seguridad, CORS restrictivo, sanitización en ambos lados.
- **Resiliencia:** TimeLimiter 5s, circuit breaker 50% en 10 llamadas, fallback determinista, caché por hash de canciones.
- **Prompt versionado** en `src/main/resources/prompts/recommendations-v1.txt`.
- **Documentación:** 6 archivos (`00` a `05`) separados por responsabilidad.

**¿Qué acepté y por qué?**

| Decisión | Motivo |
|---|---|
| **Java 25 LTS + Spring Boot 4.2.0** (luego corregido a 4.1.3 en §2.4) | Justificación de "LTS más reciente con soporte extendido" coincide con la instrucción de minimizar brechas y maximizar mantenibilidad. |
| **`ownerId` + aislamiento por usuario** | Aunque no hay roles, ignorar BOLA/IDOR sería un anti-patrón Senior. |
| **404 en vez de 403 para recursos ajenos** | Evita filtrar existencia de playlists ajenas. |
| **Interfaz `AiRecommendationClient` + decorator `FallbackAiClient`** | Cumple SOLID-D y permite cambiar de proveedor (Gemini/Ollama/Mock) sin tocar dominio. |
| **Fallback doble (200 con genéricos + 503 estructural)** (luego simplificado en §2.4) | Cubre los dos tipos de fallo sin tumbar la app. |
| **Prompt versionado en `resources/prompts/recommendations-v1.txt`** | Trazabilidad y evitar editar in place. |
| **Bucket4j en memoria con nota de Redis** | Pragmático para la prueba sin perder visión de producción. |
| **Angular 22 como frontend** | Estructura, DI y sanitización integrada. |

**¿Qué rechacé o corregí y por qué?**

| Decisión de la IA | Mi corrección | Motivo |
|---|---|---|
| Redis para rate limiting como opción inicial | Degradado a "nota para README" | Sobre-ingeniería para el alcance. |
| RS256 en vez de HS256 | Mantengo HS256 por defecto | RS256 alarga el tiempo sin valor demostrable; documentado como evolución. |
| Refresh token | Fuera de alcance inicial | Sin tiempo suficiente; documentado como mejora futura. |
| Nombre `GEMINI_AI_API_KEY` como única convención | **Reforcé `<PROVEEDOR>_AI_API_KEY`** | Permite cambiar de proveedor sin refactor. |

**Explicación con mis palabras:**

La IA propuso un diseño profesional. Yo filtré: quité sobre-ingeniería (Redis, refresh token, RS256) y reforcé la convención de variables. El resultado es un diseño Senior sin "architecture astronaut". La decisión de `ownerId` sin roles fue mía tras la sugerencia de la IA.

---

### 2.3. Interacción 3 — Reescritura de documentos por confusión

**Asistente:** Claude (Anthropic, chat)

**Prompt usado (textual):**

> "Los archivos 01, 02 y 03 están confusos, puedes volver a generarlos por favor."

**Respuesta relevante de la IA:**

- Separó responsabilidades: `01` (contexto/stack/variables), `02` (qué observable), `03` (cómo se construye).
- Eliminó duplicaciones entre documentos.
- Añadió tabla de "diferencias con la versión anterior".

**¿Qué acepté y por qué?**

- La separación por responsabilidad: cada documento responde a una sola pregunta.
- La eliminación de duplicaciones: un dato vive en un solo lugar.

**¿Qué rechacé o corregí y por qué?**

- Nada. Mi feedback fue directo y la IA lo aplicó.

**Explicación con mis palabras:**

La primera versión duplicaba información entre documentos. La IA detectó el problema y reorganizó por responsabilidad. Aprendizaje: **la primera respuesta de la IA no siempre es la mejor; el feedback humano la mejora.**

---

### 2.4. Interacción 4 — Auditorías de Claude y corrección de decisiones

**Asistente:** Claude (Anthropic, chat). Las seis auditorías se hicieron en el chat; Claude Code no se pudo integrar con el IDE por no tener plan de pago.

**Prompts usados (textuales, resumen de las auditorías):**

> 1. "Audita la coherencia entre `00` y `01`–`05`."
> 2. "Verifica que las versiones del stack existan y sean estables al día de hoy."
> 3. "Compara los documentos con los PDFs originales y detecta huecos funcionales."
> 4. "Actúa como el evaluador Senior y critica las decisiones tomadas."
> 5. "Evalúa si Kiro puede implementar el proyecto sin ambigüedades."
> 6. "Audita la seguridad de `03-ARQUITECTURA-Y-SEGURIDAD.md`."

**Respuesta relevante de la IA (hallazgos consolidados):**

Claude entregó **6 informes** con hallazgos que se clasifican en:

- **Incompatibilidad de versiones:** Spring Boot 4.2.0 **no es compatible** con Spring AI 2.0.x. Boot 4.2.0 requiere Spring AI 2.1.0; Boot 4.1.x requiere Spring AI 2.0.x.
- **CVE:** Spring Security 7.1.2 tiene CVEs corregidas en 7.1.3. Spring AI 2.0.0/2.0.1 tienen CVE de agosto 2026.
- **Contradicciones internas:** idioma de comentarios (español vs inglés), `503 estructural` con circuit breaker OPEN >60s (contradice el estado real de Resilience4j), `LISTA_VACIA` como `motivoFallback` con `fallback=false`.
- **Huecos funcionales:** falta la colección Postman, la "Reflexión final" del AI-LOG no está planificada, el endpoint de Spotify "géneros" probablemente está obsoleto desde feb 2026 para apps nuevas.
- **Riesgos de seguridad:** prompt injection no mitigado, `ownerId = username` (hereda listas al recrear usuarios), unicidad case-insensitive solo en servicio (condición de carrera), tolerancia de reloj de 60s en validación de `exp`, consola H2 habilitada, `/auth/me` sin rate limit.
- **Decisiones débiles:** 503 estructural detectado tarde (debería ser fail-fast al arrancar), token en body vs cookie (contradicción entre `02` y `03`), falta decisión sobre almacenamiento del JWT en el frontend.

**¿Qué acepté y por qué?**

| Hallazgo | Resolución aceptada |
|---|---|
| Spring Boot 4.1.3 + Spring AI 2.0.3 | Elegí la **opción B (conservadora)**: Boot 4.1.3 es compatible con Spring AI 2.0.3 y ambos son estables. La opción A (Boot 4.2.0, ya GA) no se descartó por inestabilidad, sino por la dependencia de Spring AI 2.1.0 y por preferir la versión menos reciente. La migración futura a Boot 4.2.0 + Spring AI 2.1.0 no es compleja. |
| Spring Security sin pinear | Dejar que el BOM de Boot gestione la versión para recibir parches de seguridad automáticamente. |
| Cookie `HttpOnly` + CSRF | **Más seguro.** Acepté el coste de desarrollo adicional (CSRF, CORS con credenciales, `/auth/me`, `/auth/logout`) a cambio de mitigar XSS. |
| Eliminar el 503 estructural | El fail-fast al arrancar cubre configuraciones incompletas. Una sola conducta: `200` con `fallback=true`. |
| Eliminar `LISTA_VACIA` del enum de `motivoFallback` | Contradice `fallback=false`. Lista vacía es informativa, no un fallback. |
| Añadir `PROVIDER_AUTH_ERROR` al enum | API key inválida/revocada del proveedor (401/403) es error de configuración que debe distinguirse. |
| Spotify: lista curada como fuente primaria | El endpoint real probablemente esté restringido para apps nuevas. La lista curada es honesta y versionada. `motivoFallback=ENDPOINT_UNAVAILABLE` en lugar de `PROVIDER_DOWN`. |
| `AI_PROVIDER=mock` por defecto en `dev` | Permite al evaluador arrancar sin API key. Coherente con el requisito del módulo IA de "mockear detrás de interfaz". |
| `sub` = UUID (no username) | Evita herencia de listas al recrear usuarios. |
| UNIQUE en BD sobre `(owner_id, nombre_normalizado)` | Resuelve la condición de carrera. |
| Prompt injection con 5 capas | Es la omisión más visible para un perfil "AI Engineer". Delimitadores XML, instrucción anti-injection, límite de longitud, validación de salida, respuesta tratada como no confiable. |
| Añadir `docs/DECISIONES-FINALES.md` como fuente de verdad | Consolida las decisiones post-auditoría con prioridad sobre `01`–`05`. |

**¿Qué rechacé o corregí y por qué?**

| Sugerencia de la IA | Mi decisión | Motivo |
|---|---|---|
| Opción A (Boot 4.2.0 + Spring AI 2.1.0) como recomendación principal | **Opción B (Boot 4.1.3 + Spring AI 2.0.3)** | Preferí la combinación ya establecida y conservadora en ambientes productivos. Boot 4.2.0 ya es GA desde el 19 de noviembre de 2026, así que la razón no fue inestabilidad de Boot: fue la compatibilidad con Spring AI (4.2.0 exige Spring AI 2.1.0) y mi preferencia por no adoptar la última versión. La migración futura no es compleja. |
| Token en `localStorage` (más simple) | **Cookie `HttpOnly` + CSRF** | Priorizar seguridad aunque cueste más desarrollo. Coherente con el perfil Senior. |
| `/spotify/search` como principal | **`/spotify/genres` como principal**, `/search` adicional | El PDF pide "lista de géneros"; `/search` no cumple el requisito literal. |
| `503` para circuit breaker OPEN >60s | **Eliminado** | Contradice el estado real de Resilience4j, complica la implementación y no aporta valor: el fallback determinista es barato. |

**Explicación con mis palabras:**

Las auditorías fueron reveladoras. Claude detectó problemas que yo no vi: la incompatibilidad Spring AI ↔ Boot (que habría hecho fallar el build), el 503 inventado sobre un estado de Resilience4j que no existe como yo lo describía, y el riesgo de `ownerId = username`. Acepté la mayoría de hallazgos, pero **filtre tres decisiones de diseño** con criterio propio: preferí estabilidad sobre novedad (Opción B), seguridad sobre simplicidad (cookie sobre localStorage) y fidelidad al PDF sobre conveniencia (`/genres` como principal). Este filtro es parte del trabajo del Senior: la IA propone, el humano decide.

---

### 2.5. Interacción 5 — Cambio de workflow (Kiro revisor, no generador)

**Asistente:** Claude (Anthropic, chat)

**Prompt usado (textual):**

> "Me gustaría que Claude me ayude con la generación de código, y luego con el ID en Kiro lo puedo verificar. Cuando haya terminado todo el desarrollo, o sea, Kiro sería un segundo revisor."

**Respuesta relevante de la IA:**

- Confirmó el cambio: **Claude Code genera (plan), humano revisa en cada fase, Kiro audita al final**.
- Ajustó el `AI-LOG` para reflejar este workflow (esta sección).
- Preparó un kit de transferencia: `PROMPT-ARRANQUE.md`, `PROMPT-FASE-0` a `PROMPT-FASE-7`, `docs/AUDITORIA-KIRO.md`.

**¿Qué acepté y por qué?**

- El nuevo workflow. Mantiene el rol de Kiro como revisor independiente (más valioso que como generador para el entregable) y concentra la generación en un solo asistente con revisión humana por fase (en el plan, Claude Code).

**Explicación con mis palabras:**

Inicialmente se pensó en Kiro como generador y Claude como revisor. Cambié a que Claude Code genere porque su contexto de auditoría ya estaba maduro, y porque tener un **revisor independiente final (Kiro)** sobre el código ya generado y auditado por mí es más valioso que tener dos IAs generando en paralelo. Es una decisión de arquitectura del proceso, no del software.

**Nota posterior (qué pasó realmente):** Claude Code no se pudo usar. No tenía plan de pago para integrarlo con el IDE y el chat de Claude se quedó sin tokens. La generación de las Fases 0–4.3 la hizo **Grok** en modo copia-pega (`grok/PROMPT-ARRANQUE-GROK.md`), con el mismo esquema: yo pegaba el contexto de cada fase, revisaba el resultado y Kiro auditó al final.

---

## 2.6. Implementación — Ejecución, revisión y corrección con Kiro (cierre Fase 4 + Fase 5)

**Asistente:** Kiro (ejecutó el proyecto, corrigió, escribió tests y la Fase 5). El código de las Fases 0–4.3 lo había generado otra IA en modo copia-pega; Kiro actuó aquí como revisor y corrector. Fechas: 2026-11-25 y 2026-11-26.

**Prompts del autor (textuales; solo se normalizó la ortografía):**

> 1. "Actualmente estamos terminando la fase 4, sin embargo están sacando unos errores, quiero que corras el proyecto."
> 2. "Antes de continuar, me gustaría saber si esto ya está: …" (pegó una revisión externa de la propuesta 4.3 con 9 problemas; ver §4).
> 3. "Si haz las correcciones necesarias"
> 4. "Me voy con la recomendación que diste" (el género queda como texto libre; la lista curada solo sugiere valores).
> 5. "Hagamos las partes pendientes de la fase, explícame en detalle qué necesitas que te ayude o haga" / "Verifica si tienes acceso".
> 6. "En nuestro proyecto estamos cumpliendo estos lineamientos?" (pegó la especificación del reto; resultado: faltaban Spotify, frontend, colección Postman, README y AI-LOG completo).

**Verificación hecha por el autor (acción humana):** probó con su propia cuenta los endpoints de Spotify desde su terminal y reportó los códigos: `available-genre-seeds` → **404**, `browse/categories` → **403**, `search` → **200** (con resultados reales). De ahí salió la decisión de la lista curada. También levantó el backend en su terminal con sus credenciales para que Kiro llamara a la API real.

**Hallazgos al ejecutar el proyecto real (ninguno lo detectaban los tests existentes):**

| # | Hallazgo | Corrección |
|---|---|---|
| 1 | `AiConfig` declaraba un `ChatClient.Builder` que se inyectaba a sí mismo: **la app no arrancaba** (ciclo de beans). | Eliminado: Spring AI ya autoconfigura el bean. |
| 2 | Spring AI autoconfiguraba Gemini y Ollama aunque `AI_PROVIDER=mock`: fallaba por falta de API key. | `spring.ai.model.chat` (`none`/`google-genai`/`ollama`) vía `SPRING_AI_MODEL_CHAT`. |
| 3 | La cookie `XSRF-TOKEN` **nunca se emitía** (token CSRF diferido): ningún POST/DELETE podía funcionar desde un cliente real. Los tests no lo detectaban porque obtenían el token del endpoint auxiliar `GET /auth/csrf`, que sí lo materializaba; ningún otro endpoint lo hacía. | Filtro que fuerza la escritura de la cookie. |
| 4 | La estrategia de sesión de CSRF borraba la cookie tras cada petición autenticada (segundo POST seguido → 403). | Estrategia desactivada (app stateless). |
| 5 | Rate limit de recomendaciones duplicado en filtro y controller (mismo bucket): cada petición gastaba 2 tokens, límite real ≈ 2/min en vez de 5. | Se dejó solo el filtro; test de regresión. |
| 6 | Los clientes Gemini/Ollama no tenían timeout HTTP efectivo: la propiedad `spring.http.client(s).read-timeout` de Boot **no llegaba** al cliente de Spring AI (lo reveló un test con WireMock: la llamada esperó 3 s). | `RestClient.Builder` explícito para Ollama y `HttpOptions.timeout` para Gemini. |
| 7 | Spring AI reintentaba los 5xx con backoff de hasta ~50 s, superando el `TimeLimiter` de 5 s. | `spring.ai.retry.max-attempts=1`. |
| 8 | Mapeo de errores por texto del mensaje (`"401"`, `"permission"`), `parseAndValidate` duplicado, `log.debug` con la respuesta cruda del LLM. | `AiErrorMapper` (por tipo/código HTTP), `AiResponseParser` único, log eliminado. |
| 9 | El JDK `HttpClient` negociaba HTTP/2 contra WireMock y fallaba. | HTTP/1.1 forzado. |
| 10 | Faltaban los rate limits de `/lists/**`, `/auth/me`, `/auth/logout` y login por usuario (03 §4.5). | Implementados con tests. |

**Fase 5 — Spotify:** `/spotify/genres` intenta el endpoint real y cae a la lista curada (`ENDPOINT_UNAVAILABLE`); `/spotify/search` es la integración real, verificada manualmente contra Spotify real con el backend en `dev` (200 con canciones reales; `limit=11` → 400).

**Lo que Kiro se equivocó o dejó mal en esta etapa (honestidad):**
- Lanzó el build antes de terminar de editar en varias ocasiones (condición de carrera propia) y tuvo que repetirlo.
- Un reemplazo multilínea falló en silencio por saltos de línea CRLF; la compilación no lo reveló hasta revisar el resultado.
- Al principio no comprobó que los tests de CSRF fallaran sin el arreglo (después se hizo con pruebas de mutación; ver §2.7).

**Estado de los pendientes que esta sección dejó abiertos:**
- `gemini-2.0-flash` sin verificar → **resuelto en §2.7**: Google lo apagó el 2026-06-01.
- Doble mapeo de excepciones entre `RecommendationService` y `FallbackAiClient` → **resuelto**: `AiFailureClassifier` es el único lugar.
- `ObjectMapper` de Jackson 2 (Boot 4 usa Jackson 3 por defecto) → **sigue pendiente**: funciona, es deuda técnica.

**Qué se aprobó:** el autor aprobó todas las correcciones de esta etapa («Si haz las correcciones necesarias»). Todas se aplicaron con Kiro y quedaron en commits.

**Explicación con mis palabras:**

*(Borrador redactado por Kiro a partir de lo que ocurrió; el autor debe leerlo, corregirlo con sus propias palabras y confirmarlo.)*

El proyecto no arrancaba porque yo tenía una clase `AiConfig` que declaraba un bean de Spring AI (`ChatClient.Builder`) y recibía ese mismo tipo como parámetro. Spring ya crea ese bean por su cuenta, así que el mío dependía de sí mismo y el contexto fallaba al iniciar. Los tests no lo mostraban porque no levantaban la aplicación completa. Al quitar la clase, el bean automático bastó. Además, Spring AI intentaba configurar Gemini y Ollama aunque yo usara el proveedor `mock`, y fallaba por falta de API key; lo resolví indicando explícitamente qué modelo de chat activar.

La cookie CSRF no se emitía porque Spring Security 7 difiere la creación del token: solo lo genera cuando alguien lo lee. Ningún endpoint lo leía, así que el navegador nunca recibía la cookie `XSRF-TOKEN` y cualquier POST o DELETE real habría dado 403. Mis tests no lo detectaban porque pedían el token a un endpoint auxiliar (`GET /auth/csrf`) que sí lo generaba, un atajo que no existe para un cliente real. Añadí un filtro que fuerza la escritura de la cookie, y quité el endpoint auxiliar.

El timeout real cambió porque la propiedad de Boot (`read-timeout`) no llegaba al cliente HTTP que usa Spring AI; la llamada podía quedarse esperando mucho más de lo configurado. Lo vi con un test que simulaba un servidor lento y la llamada esperó 3 s en vez de cortar. Ahora cada cliente tiene su timeout explícito, y desactivé los reintentos de Spring AI, que podían sumar hasta unos 50 s y pasar por encima del límite de 5 s del `TimeLimiter`.

---

## 2.7. Revisión final con las skills de steering y la guía de auditoría (Kiro)

**Asistente:** Kiro. Fechas: 2026-11-25 y 2026-11-26.

**Prompts del autor (textuales, en orden):**

> 1. "Necesito que ejecutes las skills para encontrar posibles errores que pasó mientras se hizo el desarrollo" (adjuntó `.kiro/steering/`: revisión de código, auditoría de seguridad, revisión de arquitectura y estándares).
> 2. "Si, comienza por ahí" → "Si, continua con esas" → "Sigue con I6, I7 y I11" (orden de corrección propuesto por Kiro).
> 3. "La paginación es algo que nos piden? Es completamente necesario o es un detalle que se quiere adicionar" → "Si, hagamos solo el N+1, luego continúa con los arreglos menores del frontend". **Decisión del autor:** no paginar, porque el reto no lo pide.
> 4. "A todo esto, ya estamos usando una LLM para la parte de la recomendación?" (respuesta: no, solo el mock; el modelo configurado estaba retirado).
> 5. "evalúa cómo está el proyecto siguiendo esas instrucciones [`estandar_arquitectura.md`] … Si nada hace falta, usemos un modelo local pequeño, puede ser uno de Ollama, puesto que no tengo apikeys en este momento y prefiero que sea una solución local y no exponer nada de información". **Decisión del autor:** usar Ollama local, nada sale de su equipo.
> 6. "Si" (prompt v2 y filtro de canciones repetidas) → "Si, sigamos con eso" (README y Postman).
> 7. "Muévelo dentro del proyecto, registra los prompts reales … y correcciones posteriores" (este registro).

**Decisiones de alcance del frontend (autor):** «1. Todo el alcance 2. Mantengámoslo sin habilitar 3. Sí 4. Sí, los tests para probar que funcione, no es necesario ser rigurosos». Es decir: todas las vistas, reproductor deshabilitado (no hay fuente de audio), carpeta `frontend/` junto al backend y pruebas básicas.

### Defectos encontrados y corrección (con commit)

| ID | Hallazgo | Cómo se confirmó | Corrección | Commit |
|---|---|---|---|---|
| B1 | El prompt **nunca incluía las canciones**: el código reemplazaba `%s` y la plantilla usa `{{canciones}}`. Un título `</canciones>…` tampoco se neutralizaba. | Test desechable: el prompt generado no contenía el título y conservaba `{{canciones}}`. | Reemplazo del marcador correcto, fallo al arrancar si falta, neutralización de `<`, `>` y caracteres de control. | `b780fbd` |
| B2 | Con proveedores reales, **timeout y 401 salían como `PROVIDER_DOWN`**: `unwrap()` llegaba a la causa raíz (la excepción de red) saltándose la excepción de dominio. | Test desechable con causas anidadas realistas. | `AiFailureClassifier`: primera excepción de dominio de la cadena. | `b780fbd` |
| B3 | **El circuit breaker no protegía**: la llamada al proveedor se lanzaba antes de preguntarle al breaker; con el circuito abierto el proveedor igual recibía la petición. | Test desechable: circuito abierto → el proveedor se llamó 1 vez. | El breaker pasa a ser la capa exterior. | `b780fbd` |
| B4 | Ruta inexistente, método o tipo de contenido no soportado y caracteres de control → **500**. | `curl` contra el servidor real. | Handlers 404/405/415, `InvalidTextException`, `RequestRejectedHandler`, validación de caracteres de control en todos los DTO. | `5a59bf2` |
| B5 | El `JWT_SECRET` de `.env.example` (51 caracteres) **pasaba la validación**: quien lo copiara arrancaba con un secreto público. | Lectura del validador. | El validador rechaza placeholders; el ejemplo ya no es un valor usable. | `5a59bf2` |
| I1 | Una transacción de BD seguía abierta mientras se esperaba al proveedor de IA. | Prueba de mutación (ver abajo). | La lectura de la lista termina antes de llamar a la IA. | `9f4c8eb` |
| I2 | Caché no atómica: peticiones idénticas simultáneas llamaban varias veces al modelo. | Test concurrente (8 hilos → 1 llamada). | `cache.get(key, loader)`; los fallbacks no se guardan. | `9f4c8eb` |
| I3 | `GET /lists` hacía 1 + N consultas (contaba canciones cargando cada colección). | Estadísticas de Hibernate: 6 sentencias para 5 listas. | Proyección JPQL con `size(canciones)`: 1 sentencia. **Paginación descartada por el autor.** | `014c6f0` |
| I4 | `/actuator/health` mostraba ruta de disco y estado de la BD a cualquier usuario. | `curl`. | `show-details: never`. | `9f4c8eb` |
| I5 | `nombreNormalizado` usaba `toLowerCase()` del locale por defecto (con locale turco rompe la unicidad). | Test con `tr-TR`. | `TextNormalizer` (`Locale.ROOT`). | `9f4c8eb` |
| I6 | Kiro marcó `CsrfValidationFilter` como "duplicado". **Fue un diagnóstico erróneo**: sin el filtro, `POST /lists` sin token devolvió 201. El resource server exime del `CsrfFilter` de Spring a toda petición cuyo token encuentra (aquí, la cookie). | Los propios tests de CSRF fallaron al borrarlo. | Se **restauró** el filtro con Javadoc que explica por qué, rechazo de tokens vacíos y comparación en tiempo constante; solo se eliminó el endpoint `GET /auth/csrf`, que no estaba en el contrato. | `8135c0c` |
| I7 | La configuración de Resilience4j del `application.yml` no se leía (valores fijos en código). | Lectura. | `ResilienceConfig` toma el breaker y el limitador del yml; test que prueba que cambiar la propiedad cambia el bean. | `8135c0c` |
| I10 | El `catch` de duplicado era inalcanzable (el INSERT se ejecutaba al confirmar la transacción) y cualquier violación de integridad se etiquetaba como duplicado. | Prueba de mutación y test de 6 creaciones simultáneas. | `saveAndFlush` dentro del `try`; otras violaciones responden 500. | `9f4c8eb` |
| I11 | `expiresAt` del login no coincidía con el `exp` del JWT y `/auth/me` decodificaba el token dos veces. | Test que compara ambos con el claim. | `IssuedToken` con precisión de segundos. | `8135c0c` |
| — | Frontend (revisión de código): 9 suscripciones sin `takeUntilDestroyed`, métodos llamados desde plantillas, componente de ~370 líneas, booleanos sin prefijo `is/has`, URL de la API fija. | Reglas del steering. | `SongPicker` extraído, `computed()`, renombres, `src/environments`. Se encontró de paso que **Enter en el selector enviaba el formulario** de la lista. | `bd240c7` |
| — | `max_tokens` no configurado; modelo Gemini fijo en `gemini-2.0-flash` (**apagado por Google el 2026-06-01**); faltaban tests 401 sobre el propio endpoint de recomendaciones. | Guía `estandar_arquitectura.md` + búsqueda web. | `AI_MAX_OUTPUT_TOKENS`, `GEMINI_AI_MODEL` obligatorio sin valor por defecto, `AI_TIMEOUT_SECONDS`. | `eb87bda` |
| — | Con un LLM real (llama3.2) el modelo **repetía canciones de la lista y pegaba el artista al título** (copiaba el formato `- título - artista - género` de la entrada). | Prueba con Ollama real (antes/después abajo). | Prompt v2 (entrada JSON y reglas explícitas) y `RecommendationFilter`. | `b30ffc0` |
| — | La semilla del usuario `demo` (contraseña pública) se cargaba en **todos los perfiles**, no solo `dev`. Mensaje de login incorrecto en inglés. | Lectura de `application.yml` + servidor real sin perfil `dev`. | Semilla solo en `application-dev.yml` + test sobre la configuración; mensaje fijo en español. | `79f4fe1` |

**Pendiente a propósito:** límite de login por IP detrás de un proxy inverso (`forward-headers-strategy`; es de despliegue, está en el README) y el `ObjectMapper` de Jackson 2.

### Prueba con un LLM real (Ollama, `llama3.2` de 3B, local)

- Antes de la v2, en las tres listas probadas el modelo repitió canciones de la lista; en pop mezcló título y artista.
- Con la v2: pop devolvió 5 canciones nuevas con título y artista separados. Con el mismo prompt de rock, 3 de 4 intentos dieron 5 canciones nuevas y 1 de 4 dio una sola; probar temperatura 0.7 y 0.3 dio el mismo resultado, así que no se cambió.
- Tiempos: 5–26 s con el modelo caliente, ~34 s la primera llamada. Hace falta `AI_TIMEOUT_SECONDS=90`; la segunda petición idéntica sale de la caché en milisegundos.
- Las `razon` siguen conteniendo datos inventados (ej. "riff de guitarra" en *So What*): limitación del modelo pequeño, sin arreglo en código.
- **Gemini no se ha ejecutado** contra la API real (no hay clave).

### Cómo se comprobó que los tests detectan los defectos (pruebas de mutación)

Se reintrodujo a propósito el código defectuoso y se confirmó que el test fallaba antes de restaurarlo: `@Transactional` durante la llamada a la IA (I1), `save` en lugar de `saveAndFlush` (I10) y la lectura de colecciones por lista (I3: 6 sentencias en lugar de 1). Los defectos B1–B3 se demostraron antes de corregirlos con un test desechable (ya borrado). No se hizo la mutación con I2, I4 ni I5.

### Errores de Kiro en esta etapa

- **I6:** diagnóstico equivocado (ver tabla). Lo corrigieron los tests de CSRF, no la revisión.
- Lanzó compilaciones antes de terminar de editar (más de una vez) y dejó un archivo de test con BOM que rompió la compilación.
- En el AI-LOG anterior afirmó que el modelo `gemini-2.0-flash` estaba "sin verificar su disponibilidad": estaba apagado.

### Lecciones

1. **El mock oculta los defectos del módulo de IA.** `MockAiClient` ignora el prompt y `@MockitoBean` sustituía al decorador de resiliencia: 62 tests en verde y el prompt vacío, el breaker decorativo y los motivos de fallback incorrectos. Solo se vieron con el decorador real y con un LLM real.
2. **MockMvc no equivale a un servidor real**: un carácter de control en la URL se comportó distinto; se verificó con `curl`.
3. **Los modelos y endpoints externos caducan** (Gemini 2.0 Flash, `available-genre-seeds`): nunca deben ir fijos en el código.
4. **Una revisión puede equivocarse** (I6): los tests de seguridad son la red de seguridad de la propia revisión.

---

## 3. Ejercicio 1 — Endpoint con IA integrada

**Prompt(s) usado(s):**

Prompt de arranque enviado a Grok en modo copia-pega (`grok/PROMPT-ARRANQUE-GROK.md`), textual:

> Vas a asistir en el desarrollo de una prueba técnica **Senior AI Engineer**. El proyecto ya tiene documentación cerrada. **Tu rol no es decidir arquitectura, sino generar código completo y correcto según los documentos.**
>
> **Modo de trabajo (crítico):** NO tienes acceso a mi repositorio. Yo copio tu código a archivos, ejecuto los comandos y te pego los resultados. Por lo tanto: (1) nunca asumas que un archivo existe; (2) imprime archivos COMPLETOS, sin resumir con `// ...`; (3) incluye el path como header de cada bloque de código; (4) no ejecutes comandos; (5) no te detengas "esperando confirmación" entre archivos; (6) si algo falla al compilar o testear, te pego el error y corriges SOLO los archivos afectados; (7) cuando dudes, pregunta antes de inventar.
>
> **Contexto:** monorepo con `backend/` (Spring Boot) y `frontend/` (Angular); `DECISIONES-FINALES.md` tiene prioridad. Stack: Java 25, Spring Boot 4.1.3, Spring AI 2.0.3, Angular 22, H2, Resilience4j 2.4.0, Bucket4j 8.20.0, Caffeine 3.1.8, Lombok. Autenticación: JWT en cookie HttpOnly + CSRF obligatorio. Módulo IA: `AiRecommendationClient` con fallback determinista; **nunca 500 por fallo de IA**. Convenciones: código y comentarios en inglés; mensajes al usuario y docs en español.
>
> **Reglas no negociables:** (1) cero secretos en código; (2) timeout + circuit breaker + fallback en el cliente IA, fallback determinista; (3) nunca escapar HTML al persistir; (4) nunca cachear fallbacks; (5) nunca exponer stack traces, `ErrorResponse` uniforme también en filtros; (6) prompt injection: delimitadores XML + instrucción anti-injection + límite de longitud + validación de salida JSON.
>
> **Antes de la Fase 0:** confirma que entendiste el modo copia-pega, resume las 6 decisiones clave en 6 bullets y espera mi primer mensaje de fase con el contexto pegado.

**Mensajes de fase:** el autor no conservó los mensajes literales de las Fases 4.1, 4.2 y 4.3 (contexto pegado + instrucción). El esquema fue el definido en el prompt de arranque: pegar el contexto de la fase en Grok y copiar los archivos generados al proyecto. No se reconstruyen aquí para no inventarlos.

**Respuesta / código relevante de la IA (fragmentos que luego se corrigieron):**

```java
// AiConfig (4.3): un bean que recibe como parámetro otro del mismo tipo → ciclo; la app no arrancaba
@Bean
public ChatClient.Builder chatClientBuilder(ChatClient.Builder builder) { return builder; }

// GeminiAiClient (4.3): clasifica el error por el TEXTO del mensaje
String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")
        || msg.contains("api key") || msg.contains("permission")) {
    return new AiProviderAuthException("Error de autenticación con Gemini", ex);
}

// PromptTemplates (4.1): la plantilla usa {{canciones}}, el código reemplaza "%s" → el LLM nunca recibía las canciones
return template.replace("%s", bloque);

// FallbackAiClient (4.1): el proveedor se lanza DENTRO del TimeLimiter, antes de consultar al breaker
CompletableFuture<RecommendationResult> future = timeLimiter
        .executeCompletionStage(scheduler, () -> delegate.recommend(canciones, promptVersion))
        .toCompletableFuture();
CompletableFuture<RecommendationResult> withCb = circuitBreaker.executeCompletionStage(() -> future).toCompletableFuture();
// …y clasifica por la causa raíz: while (t.getCause() != null && t != t.getCause()) { t = t.getCause(); }

// RecommendationService (4.2): transacción abierta durante la llamada a la IA, y caché no atómica
@Transactional(readOnly = true)
public RecommendationResponse recomendar(String playlistNombre, UUID ownerId) { … .join() … getIfPresent(…) … put(…) }
```

La propuesta 4.3 sugería además `@Qualifier("googleGenAiChatClientBuilder")` y `("ollamaChatClientBuilder")`: la revisión externa no pudo encontrar esos nombres en la documentación (ver §4).

**¿Qué acepté y por qué?**

| Componente | Estado | Motivo |
|---|---|---|
| Interfaz `AiRecommendationClient` + implementaciones `Mock`/`Ollama`/`Gemini` | Aceptado | Desacopla el dominio del proveedor (SOLID-D, `03 §3`). |
| `FallbackAiClient` como decorador | Aceptado, con correcciones (B2, B3) | Una sola política de timeout, breaker y fallback para cualquier proveedor; el controller nunca ve un fallo del proveedor. |
| `FallbackCatalog` determinista por género | Aceptado | Fallback no aleatorio (`DECISIONES-FINALES §12.5`). |
| Contrato `RecommendationResult` y tabla de `motivoFallback` | Aceptado | Es el contrato de `02 §2.3`. |
| Caché Caffeine, límite de 20 canciones / 2000 caracteres, rate limit 5/min | Aceptado, con correcciones (I2 y doble consumo del bucket) | Control de costo (`03 §5.5`). |
| Prompt en archivo versionado | Aceptado, con correcciones (B1) y luego v2 | `03 §5.6`. |

**¿Qué rechacé o corregí y por qué?**

Cada corrección está en la tabla de §2.7 y en §2.6. Resumen de lo corregido en el cliente de IA: `AiConfig` eliminado (ciclo de beans); sin timeout HTTP efectivo → timeouts explícitos; clasificación por texto del mensaje → por tipo y código HTTP (`AiErrorMapper`); `parseAndValidate` duplicado → `AiResponseParser`; `log.debug` con la respuesta cruda → eliminado; reintentos de Spring AI → desactivados; prompt sin canciones → corregido; breaker decorativo → capa exterior; clasificación por causa raíz → `AiFailureClassifier`; transacción durante la llamada → fuera; caché no atómica → atómica.

**Explicación con mis palabras:**

*(Borrador redactado por Kiro leyendo el código; el autor debe leerlo, reescribirlo con sus palabras y confirmarlo.)*

La petición entra por `GET /lists/{nombre}/recommendations`. Antes de llegar al controlador, un filtro aplica el límite de 5 peticiones por minuto y usuario. El controlador toma el usuario autenticado de la cookie y llama a `RecommendationService`.

El servicio lee la lista en una transacción corta, que termina antes de llamar a la IA para no mantener una conexión de base de datos ocupada mientras se espera a un tercero. Si la lista es de otro usuario o no existe, responde 404; si está vacía, devuelve un mensaje sin llamar a la IA. Con las canciones arma una clave de caché (usuario, lista y contenido) y consulta Caffeine. Si ya hay resultado, lo devuelve sin llamar al proveedor. Si no, hace una sola llamada por clave aunque lleguen peticiones iguales a la vez.

La llamada pasa por `FallbackAiClient`, que envuelve a cualquier proveedor (mock, Ollama o Gemini) con timeout, circuit breaker y el fallback. El proveedor recibe un prompt versionado en el que las canciones van entre delimitadores y con una instrucción contra prompt injection. La respuesta se valida con `AiResponseParser`: debe ser JSON con la forma esperada, y si no lo es, se trata como fallo. Después `RecommendationFilter` quita las canciones que el usuario ya tiene.

Si todo salió bien, el resultado se guarda en caché. Si el proveedor falló, tardó, el circuito está abierto o la respuesta no sirvió, se devuelve una lista determinista del catálogo según el género, con `fallback=true` y un `motivoFallback`. Ese resultado nunca se guarda en caché, para que la siguiente petición vuelva a intentar con la IA. El controlador nunca ve el error del proveedor: siempre responde 200 con recomendaciones.

---

## 4. Ejercicio 2 — Revisión crítica de código generado por IA

**Prompt(s) usado(s):**

1. El prompt de arranque de Grok (ver §3) y el mensaje de la Fase 4.3 (generar los clientes Gemini/Ollama), que no se conservó textualmente.
2. **Revisión externa de la propuesta 4.3** (hecha en el chat de Claude y pegada por el autor a Kiro). Texto relevante, resumido sin cambiar su contenido:

| # | Problema señalado | Gravedad |
|---|---|---|
| 1 | `AiConfig` es dañino: declara un `ChatClient.Builder` que recibe como parámetro otro `ChatClient.Builder`; quedaría dependiendo de sí mismo. | Alta |
| 2 | La premisa de 4.3.7 es incorrecta: los `@Qualifier("googleGenAiChatClientBuilder")` / `("ollamaChatClientBuilder")` no aparecen en la documentación. | Alta |
| 3 | La solución oficial es `spring.ai.chat.client.enabled=false` y crear los `ChatClient` a mano. | Alta |
| 4 | Imports de Jackson 2 (`com.fasterxml…`) mientras Boot 4 usa Jackson 3 por defecto. | Alta |
| 5 | Sin timeout HTTP en los clientes (exigido por `03 §5.1` y `04 §9`). | Alta |
| 6 | Mapeo de errores frágil (busca "401" o "permission" en el mensaje; `instanceof TimeoutException` casi nunca se cumple con `RestClient`). | Media |
| 7 | `parseAndValidate` duplicado en ambos clientes. | Media |
| 8 | Cero tests para el código con más lógica; `log.debug` vuelca la respuesta cruda del LLM. | Media |
| 9 | `gemini-2.0-flash` no verificado. | Baja |

La revisión también advertía sobre la fase 4.2 (doble fallback; rate limit en el controller con `ConcurrentMap` sin límite) y sobre el propio AI-LOG.

**Análisis / casos faltantes / hallazgos:**

Checklist aplicado al código generado por la IA, con el estado de **la propuesta inicial** y el **estado actual** verificado:

| Pregunta | Propuesta inicial | Estado actual |
|---|---|---|
| ¿API key hardcodeada o por variable de entorno? | ✅ Variable de entorno | ✅ Sin secretos en lo versionado (barrido con `git grep`) |
| ¿Hay timeout o la llamada puede colgar? | ❌ Solo el `TimeLimiter`; el HTTP de los clientes reales no tenía timeout efectivo | ✅ Timeouts de conexión y lectura explícitos (`AI_TIMEOUT_SECONDS`) |
| ¿Existe fallback o la excepción llega al controller? | ⚠️ Existía, pero el motivo salía mal con proveedores reales | ✅ `200` + `fallback=true`; motivos correctos (`AiFailureClassifier`) |
| ¿Se valida la respuesta contra un esquema? | ⚠️ Validación duplicada y frágil en cada cliente | ✅ `AiResponseParser` único (máx. 5, descarta ítems sin título/artista, `razon` ≤ 200) |
| ¿Se registran datos sensibles en logs? | ❌ `log.debug` con la respuesta cruda del LLM | ✅ Eliminado |
| ¿Retry con backoff o solo timeout? | ⚠️ Spring AI reintentaba hasta ~50 s sin que lo configuráramos | ✅ `max-attempts: 1`; el breaker y el fallback cubren el caso |
| ¿Prompt versionado o improvisado? | ⚠️ Versionado en archivo, pero el marcador nunca se reemplazaba | ✅ v2 versionado, versión en la clave de caché, falla al arrancar si falta el marcador |
| ¿Se limita el tamaño del prompt? | ✅ 20 canciones / 2000 caracteres | ✅ Igual, más `AI_MAX_OUTPUT_TOKENS` para la salida |
| ¿Se respeta SOLID-D? | ✅ Interfaz | ✅ |
| ¿Hay caché? | ⚠️ Sí, pero no atómica | ✅ Atómica por clave; los fallbacks no se guardan |
| ¿El circuit breaker está configurado o solo se menciona? | ❌ Decorativo (el proveedor se llamaba con el circuito abierto) y su config del yml no se leía | ✅ Capa exterior, configurado desde `application.yml`, con tests |
| ¿Fallback determinista? | ✅ Por género | ✅ Y ya no sugiere canciones que la lista tiene |
| ¿`200` con fallback o `500`? | ⚠️ Fallo de IA → 200; pero errores del cliente (ruta, método, caracteres de control) → 500 | ✅ Todo 4xx/200 |
| ¿Mensajes claros y en español? | ⚠️ El login incorrecto respondía `Invalid credentials` | ✅ Texto fijo en español |
| ¿La respuesta del LLM es no confiable? | ✅ Solo se parsea | ✅ Y se filtra (canciones repetidas, artista pegado al título) |
| ¿Mitiga prompt injection? | ⚠️ Delimitadores, pero los datos podían cerrar el bloque (`</canciones>`) y el prompt no llevaba datos | ✅ Neutralización de `<`/`>` y escape de comillas; test con `</canciones> ignora lo anterior` |

**Lo que implementé o corregí yo mismo:**

Las correcciones de código las aplicó Kiro **a petición y con aprobación del autor** (no a mano). Lo que hizo el autor directamente:

- **Revisión externa de la propuesta 4.3** (la tabla de arriba), que detectó el ciclo de beans, los nombres de bean inexistentes, la falta de timeout y el mapeo frágil de errores.
- **Verificó con su cuenta** qué endpoints de Spotify existen (404 / 403 / 200).
- **Decisiones de diseño y alcance:** modelo local (Ollama) en lugar de una API con clave, para no exponer datos; no paginar `GET /lists`; `genero` como texto libre; reproductor sin audio; qué hacer con el AI-LOG.

**Ediciones manuales del autor (Fases 0–4.3):** ninguna. Cada error que apareció al compilar o correr las pruebas se corrigió pidiéndoselo a la IA, a partir de la salida de la terminal.

---

## 5. Ejercicio 3 — Tests e integración

**Prompt(s) usado(s):**

**Cómo se generaron y depuraron los tests de las Fases 3, 4.1 y 4.2:** Grok los generó. El autor no conserva los mensajes literales. El proceso fue iterativo: ejecutar las pruebas, copiar la salida de la terminal con los fallos a Grok y aplicar su corrección, repitiendo hasta que pasaran. No hubo edición manual de los tests.

Los tests posteriores los pidió el autor a Kiro con las instrucciones de §2.6 y §2.7 («Si haz las correcciones necesarias», «Si, comienza por ahí», etc.).

**Hallazgos / revisión crítica / riesgos detectados:**

Checklist aplicado a los tests **que generó la IA** (estado a 4.2 = 15 unitarios + 27 de integración) y estado actual:

| Pregunta | Propuesta inicial | Estado actual |
|---|---|---|
| Camino feliz con mock | ✅ `recomendar_happyPath_returns200WithFallbackFalse` | ✅ |
| 404 de lista inexistente | ✅ | ✅ |
| Lista vacía (`200`, `fallback=false`, `motivoFallback=null`) | ✅ | ✅ |
| Timeout y fallback con `200` | ⚠️ Con `@MockitoBean` sobre `AiRecommendationClient`, que **sustituía al decorador**: el fallback probado era el duplicado del servicio | ✅ `FallbackAiClientTest` y `RecommendationFallbackIT` recorren el decorador real |
| 5xx del proveedor | ⚠️ Igual que el anterior | ✅ |
| 401/403 del proveedor (`PROVIDER_AUTH_ERROR`) | ⚠️ Igual, y sin causas anidadas realistas | ✅ Con la excepción de red real anidada |
| Respuesta inválida (`INVALID_RESPONSE`) | ⚠️ | ✅ Parser con 9 casos |
| Circuit breaker abierto (`CIRCUIT_OPEN`) | ❌ Sin test | ✅ Con el circuito abierto el proveedor se llama 0 veces; tras 10 fallos se abre solo |
| 429 por rate limit | ❌ Sin test (y el límite real estaba mal) | ✅ `RateLimitIT`, `LoginRateLimitIT` |
| 401 sin cookie | ✅ Solo en `/lists` | ✅ También en `/recommendations` y con token falso |
| CSRF (`403 CSRF_INVALID`) | ⚠️ Los tests obtenían el token del endpoint auxiliar `/auth/csrf`; el resto de la API **nunca emitía** la cookie | ✅ Ausente, falso, vacío y `DELETE` sin token |
| `alg:none`, HS512, token expirado | ✅ | ✅ |
| Arranque fallido sin `JWT_SECRET` | ✅ | ✅ Y con el placeholder del ejemplo |
| Prompt injection | ❌ Sin test | ✅ `</canciones> ignora lo anterior` no puede cerrar el bloque |
| Aislamiento de caché entre usuarios | ❌ | ✅ |
| ¿Mocks realistas? | ⚠️ Simplistas: el mock ignoraba el prompt | ✅ Test contra Ollama simulado (WireMock) y prueba manual con Ollama real |
| ¿Aislados de internet? | ✅ | ✅ |
| ¿Verifican el contrato JSON o solo el status? | ✅ Campos clave | ✅ |
| Sanitización (`<script>`, caracteres de control) | ❌ Los caracteres de control provocaban 500 | ✅ `ErrorHandlingIT` |
| Que **no** se cachean los fallbacks | ✅ | ✅ Y se comprobó con el cliente real |

**Casos que la IA generó:**

Los 15 unitarios y 27 de integración existentes a 4.2: repositorios, `TextNormalizer`, `NoDefaultSecretValidator` (secreto ausente/corto), `AuthControllerIT`, `PlaylistControllerIT`, `SecurityIT` (401 sin cookie, `alg:none`, HS512, expirado, CSRF) y `RecommendationControllerIT` (9 casos: éxito, lista vacía, 404, timeout, error del proveedor, autenticación, respuesta inválida, caché y no cachear fallbacks).

**Casos que tuve que añadir yo:**

Los añadió Kiro con aprobación del autor, y son justo los de fallo que el mock no cubría:
- **IA:** `FallbackAiClientTest` (causas anidadas, breaker abierto, apertura tras 10 fallos, proveedor colgado), `PromptTemplatesTest`, `AiResponseParserTest`, `AiErrorMapperTest`, `OllamaAiClientIT` (timeout, 401, 500, JSON basura contra WireMock), `RecommendationServiceTest` (caché atómica con 8 hilos, fallback no cacheado, canciones repetidas), `RecommendationFilterTest`, `RecommendationFallbackIT`, `RecommendationTransactionIT`.
- **Seguridad y errores:** `ErrorHandlingIT` (404/405/415/caracteres de control), CSRF en `SecurityIT`, `RateLimitIT`, `LoginRateLimitIT`, `SessionExpiryIT`, `SeedProfileConfigTest`, validación del `JWT_SECRET` de ejemplo y del modelo de Gemini.
- **Persistencia:** `PlaylistRaceIT` (6 creaciones simultáneas), `PlaylistListingIT` (cuenta las sentencias SQL), `PlaylistServiceTest`, `PlaylistMapperTest`.
- **Spotify:** `SpotifyControllerIT` (21 casos).
- **Configuración:** `ResilienceConfigIT` y `ResilienceConfigOverrideIT`.
- **Frontend:** 26 pruebas (guard, interceptores, servicios, utilidades, login y `SongPicker`).
- **Colección Postman:** 27 peticiones y 52 aserciones, ejecutadas con Newman contra el backend real.

Total actual: backend **82 unitarias y 91 de integración**; frontend **26**.

**Decisiones tomadas:**

- **Prioridad:** resiliencia del módulo de IA, seguridad y aislamiento por usuario (los criterios del reto: «tests que cubran escenarios de fallo, no solo happy path»).
- **Frontend:** el autor pidió pruebas «para probar que funcione, no es necesario ser rigurosos»: se cubrió lógica (guard, interceptores, servicios, formulario de login, selector de canciones) y no se probaron los componentes de página.
- **Sin cobertura medida:** no hay JaCoCo; `04 §4` pide 70 % en servicios y clientes y no se ha medido.
- **Pruebas que se dejaron fuera:** Gemini real (sin clave), carga/concurrencia a escala, frontend end-to-end automatizado (se hizo una prueba manual con un navegador real).
- **Los tests no son tautológicos:** se reintrodujo el defecto en tres casos y los tests fallaron (ver §2.7).

---

## 6. Reflexión final

> **Sección exclusivamente humana.** No delegable a la IA.

### 6.1. ¿Dónde falló, alucinó o se equivocó la IA durante esta prueba?

**Fase de diseño (documentada):**

1. **La primera versión de los documentos `01`, `02`, `03` estaba confusa.** Mezclaba información y duplicaba contenido. Requirió una segunda iteración completa. **Lección:** la IA puede tener el contenido correcto pero fallar en la organización.

2. **La IA propuso inicialmente versiones incompatibles.** Spring Boot 4.2.0 con Spring AI 2.0.x no compila. La incompatibilidad solo se detectó gracias a una auditoría posterior. **Lección:** las versiones de la IA no son confiables sin verificación.

3. **La IA propuso inicialmente Redis, RS256 y refresh token** para el diseño. Eran decisiones correctas técnicamente pero sobre-ingeniería para el alcance. **Lección:** la IA tiende a sobre-diseñar cuando se le pide "lo más robusto"; el criterio de alcance es humano.

4. **La IA propuso un `503` con circuit breaker OPEN >60s** que no corresponde al modelo real de Resilience4j. Solo una auditoría externa lo detectó. **Lección:** la IA puede inventar detalles plausibles que no existen en la librería.

5. **La IA no detectó que el endpoint de Spotify de géneros probablemente está obsoleto** para apps nuevas desde feb 2026. Fue un hallazgo de Claude (chat) en auditoría. **Lección:** el conocimiento de la IA tiene fecha de corte; los endpoints externos cambian.

**Fase de implementación** *(borrador preparado por Kiro a partir de las revisiones; el autor debe confirmarlo y ampliarlo con su propia experiencia)*:

1. **Código que parecía correcto y no funcionaba.** El prompt nunca incluía las canciones: el código reemplazaba `%s` y la plantilla usaba `{{canciones}}`. El mock no leía el prompt, así que ningún test lo detectó. **Lección:** un mock que ignora la entrada no prueba la integración.
2. **Una dependencia circular y APIs inventadas.** `AiConfig` declaraba un bean que dependía de sí mismo (la app no arrancaba) y la propuesta citaba `@Qualifier("googleGenAiChatClientBuilder")` y `("ollamaChatClientBuilder")`, nombres que la revisión externa no encontró en la documentación.
3. **Configuración que se daba por hecha.** Se asumió que `spring.http.client.read-timeout` configuraba el cliente de Spring AI; no lo hacía, y los clientes reales no tenían timeout HTTP efectivo. Spring AI además reintentaba los 5xx con esperas de hasta ~50 s.
4. **Resiliencia decorativa.** El circuit breaker no impedía llamar al proveedor y el motivo del fallback salía mal con errores reales (timeout y 401 como `PROVIDER_DOWN`). La configuración de Resilience4j del `application.yml` no se leía.
5. **Un modelo ya retirado.** `gemini-2.0-flash` estaba fijo en el código y Google lo apagó el 2026-06-01. **Lección:** los nombres de modelo caducan; deben ser configuración.
6. **Seguridad que fallaba en silencio.** La cookie `XSRF-TOKEN` nunca se emitía a un cliente real; el usuario `demo` (contraseña pública) se cargaba en todos los perfiles; el `JWT_SECRET` de ejemplo pasaba la validación; entradas inválidas (ruta inexistente, caracteres de control) devolvían 500; el login incorrecto respondía en inglés.
7. **Tests que daban falsa confianza.** `@MockitoBean` sustituía al decorador de resiliencia, de modo que 62 tests en verde no ejercitaban el fallback real. Faltaban los tests de breaker abierto, 429 y prompt injection.
8. **Un LLM real reveló lo que el mock no podía.** `llama3.2` copiaba el formato de entrada en sus respuestas (título + artista juntos) y repetía canciones de la lista; hizo falta un prompt v2 y un filtro en el backend. Sus campos `razon` siguen inventando datos: es una limitación del modelo pequeño.
9. **La propia IA revisora se equivocó** (I6): marcó como "duplicado" el filtro CSRF que era la única protección efectiva. Lo corrigieron los tests.

### 6.2. ¿Qué parte del trabajo NO delegaría a la IA y por qué?

**Completado en fase de diseño:**

1. **Decisiones de alcance:** qué entra y qué no. La IA puede proponer, pero la decisión de "sin refresh token", "sin RS256", "sin Redis", "cookie sobre localStorage", "Opción B sobre A" es mía. Un evaluador Senior valora el filtro, no la acumulación de features.

2. **Revisión de seguridad final:** aunque la IA proponga BOLA/IDOR, rate limiting, sanitización, la verificación de que el código implementado realmente cumple es humana. Los LLMs suelen "olvidar" alguna capa en la generación de código largo.

3. **AI-LOG:** no delegable. La honestidad del log es mía. La IA puede redactar borradores, pero la decisión de qué acepté, qué rechacé y por qué debe venir de mí.

4. **"Explicación con mis palabras" y "Reflexión final":** no delegables. Es la sección que el evaluador lee para verificar comprensión real.

5. **Verificación de que los tests realmente fallan si el código está mal:** la IA puede generar tests tautológicos que siempre pasan. Debo verificar manualmente que cada test falla cuando debe fallar.

**Fase de implementación (pendiente):**

`[COMPLETAR — qué NO delegaría a la IA (Grok/Kiro) durante la implementación y por qué, con ejemplos concretos. Ejemplo: "No delegaría la decisión de qué excepciones capturar en el GlobalExceptionHandler; la IA tiende a capturar Exception genérica."]`

### 6.3. Autoría

**Estado actual (fase de diseño):**

| Área | % IA | % Humano | Comentario |
|---|---|---|---|
| Análisis inicial de los PDFs | 90% | 10% | IA identificó ambigüedades; yo validé y respondí las 17 preguntas |
| Diseño y arquitectura | 70% | 30% | IA propuso; yo filtré sobre-ingeniería y reforcé convenciones |
| Documentación `01`–`05` | 85% | 15% | IA redactó; yo detecté confusión y pedí reescritura; yo definí convenciones |
| `DECISIONES-FINALES.md` | 75% | 25% | IA consolidó; yo tomé las 4 decisiones clave (Opción B, cookie, `/genres`, monorepo) |
| Auditorías (Claude, chat) | 95% | 5% | IA identificó hallazgos; yo filtré los que aplicaban |
| `AI-LOG.md` | 50% | 50% | IA redactó borrador; el autor aporta las partes humanas (explicaciones y reflexión final) |
| Código | — | — | Ver la tabla final: todo el código se generó con IA y se revisó por fases |

**Estado final** (estimación del autor: diseñó la arquitectura y pidió a la IA implementarla):

| Módulo | % IA | % Humano | Comentario |
|---|---|---|---|
| CRUD `/lists` | 95% | 5% | Grok generó; el autor revisó por fase |
| Autenticación (cookie + CSRF) | 95% | 5% | Grok generó; Kiro corrigió CSRF y cookie |
| Cliente IA + fallback | 90% | 10% | El autor decidió el modelo local y el fallback; Grok y Kiro implementaron |
| Spotify | 90% | 10% | Kiro implementó; el autor verificó los endpoints con su cuenta |
| Frontend Angular | 90% | 10% | Kiro implementó; el autor definió el alcance |
| Tests | 95% | 5% | Grok y Kiro; el autor ejecutó y devolvió los fallos a la IA |
| Documentación (`README`, `AI-LOG`) | 85% | 15% | Kiro y Claude redactaron; el autor revisó |

**Nota de honestidad:** los porcentajes deben reflejar el trabajo real. Si la IA generó el 90% de una clase pero yo reescribí el 40% de sus líneas, el porcentaje humano debe reflejar eso. El evaluador valora la honestidad, no el porcentaje.

---

## 7. Observaciones sobre el uso de IA en este proyecto

- **Tres asistentes, tres roles:** Claude (chat) para diseño y auditoría conceptual, Grok para generación (Fases 0–4.3) con revisión humana por fase, Kiro como revisor independiente final y autor de la Fase 5, el frontend, el README y Postman. Cada rol fue explícito y documentado.
- **La IA propone, el humano decide:** en cada fase de diseño hubo al menos una decisión donde rechacé o filtré la propuesta de la IA con justificación técnica.
- **La auditoría externa fue clave:** la incompatibilidad Spring Boot ↔ Spring AI, el 503 inventado y el endpoint de Spotify obsoleto fueron detectados por auditoría, no por el diseño inicial. Esto confirma que un revisor independiente (humano o IA) es necesario después de la generación.
- **Prompting efectivo:** los mejores resultados se obtuvieron con prompts específicos ("actúa como X", "audita Y específicamente", "critica Z como evaluador Senior") en lugar de "genera todo el código". El prompting por rol y por tarea superó al prompting genérico.

---

**Fin del AI-LOG.**
