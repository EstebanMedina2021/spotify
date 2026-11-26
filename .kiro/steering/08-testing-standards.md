---
inclusion: fileMatch
fileMatchPattern: "**/*.spec.ts,**/*Test.java"
---

# Estándares de Pruebas Unitarias e Integración

Aplica el patrón **AAA (Arrange, Act, Assert)** en todas las pruebas:

## Backend (JUnit 5 + Mockito)
- **Servicios:** Usa `@ExtendWith(MockitoExtension.class)` con `@Mock` para repositorios e `@InjectMocks` para el servicio. No levantes el contexto completo de Spring (`@SpringBootTest`) para pruebas unitarias de lógica.
- **Controladores:** Usa `@WebMvcTest` + `MockMvc` para validar códigos HTTP, serialización JSON y validaciones `@Valid`.

## Frontend (Angular Testing)
- Mockea siempre las dependencias HTTP usando `provideHttpClientTesting()` y `HttpTestingController`.
- En pruebas del `AudioPlayerService`, mockea los métodos `play()` y `pause()` del elemento `HTMLAudioElement` para evitar errores de reproducción automática en navegadores headless.
