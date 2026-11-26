---
inclusion: always
---

# Estándares y Buenas Prácticas de Código

Aplica estas reglas en todo código nuevo o refactorizado en este repositorio:

## Reglas Universales
- **Tipado Estricto:** Prohibido `any` en TypeScript y tipos crudos (*raw types*) en Java.
- **Nombres con Intención:** Booleanos empiezan con `is`, `has`, `can` o `should`. Métodos empiezan con verbos de acción (`getUserPlaylists`, `togglePlayback`).
- **Funciones Pequeñas (SRP):** Si un método o componente supera las 40-50 líneas o tiene más de 2 niveles de anidamiento, extrae métodos o subcomponentes.

## Frontend (Angular)
- **Separación de Responsabilidades:** Los componentes (`.ts` / `.html`) solo manejan presentación e interacción UI. Toda llamada HTTP y lógica de estado vive en `services/`.
- **Clean Templates:** Evita llamar métodos complejos directamente dentro de expresiones `{{ }}` del HTML (usa Signals, `computed()` o Pipes).

## Backend (Spring Boot)
- **Arquitectura en Capas:** Estricta separación entre `Controller` (HTTP/Validación `@Valid`), `Service` (lógica de negocio) y `Repository` (Spring Data JPA). Nunca inyectes un `Repository` directamente en un `Controller`.
