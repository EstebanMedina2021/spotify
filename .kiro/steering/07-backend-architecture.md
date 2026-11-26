---
inclusion: fileMatch
fileMatchPattern: "proyecto/backend/**"
---

# Arquitectura Backend (Java / Spring Boot - Spotify Demo)

Cuando crees o modifiques código dentro de `proyecto/backend/`, respeta estrictamente la separación en capas y patrones de Spring Boot:

## 1. Estructura de Paquetes (`src/main/java/...`)
- `config/`: Configuración de Spring Security, CORS, Beans globales y recursos estáticos.
- `controller/`: Endpoints REST (`@RestController`). Solo valida entrada (`@Valid`), delega al servicio y retorna `ResponseEntity<DTO>`.
- `service/`: Lógica de negocio e interfaces de servicio. Aquí vive `@Transactional`.
- `repository/`: Interfaces que extienden `JpaRepository`. Prohibido escribir lógica de negocio aquí.
- `model/` (o `entity/`): Entidades JPA (`@Entity`). Nunca se retornan directamente en los controllers.
- `dto/`: Records o clases inmutables para `Request` y `Response` (ej. `TrackResponseDTO`, `PlaylistCreateRequest`).
- `exception/`: Excepciones de dominio personalizadas y un `@RestControllerAdvice` global que estandarice las respuestas de error (`ProblemDetail` o `ApiErrorResponse`).

## 2. Arquitectura de Streaming de Audio
- Los archivos de audio en `data/` o almacenamiento externo nunca deben cargarse completos en memoria (`byte[]`).
- Usa `UrlResource` / `FileSystemResource` junto con `ResourceRegion` o `ResponseEntity<Resource>` soportando la cabecera HTTP `Range` para permitir el *seeking* (adelantar/retroceder pista) eficiente desde Angular.

## 3. Reglas de Base de Datos y Rendimiento
- Usa inyección de dependencias por constructor (`private final`), nunca `@Autowired` en atributos.
- Configura relaciones `@ManyToOne` y `@ManyToMany` como `FetchType.LAZY` por defecto y usa `JOIN FETCH` o `@EntityGraph` cuando necesites evitar consultas N+1.
