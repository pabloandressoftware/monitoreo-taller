# FutureXCourseCatalog (fx-catalog-service)

**Autores:** Pablo Andrés Guzmán Alarcón (A00399523, [@pabloandressoftware](https://github.com/pabloandressoftware)) · Stick Martinez (A00398704, [@Stixkl](https://github.com/Stixkl)) · Alejandro Troya (A00399865, [@DavidIsGod](https://github.com/DavidIsGod))

Microservicio de catálogo. Descubre a `fx-course-service` a través de Eureka y lo consume con `RestTemplate`.
Rama `feature/resilience4j`: protege esas llamadas con **Resilience4j**.

## Qué se añadió

| Archivo | Cambio |
|---|---|
| `pom.xml` | `spring-cloud-starter-circuitbreaker-resilience4j`, `spring-boot-starter-aop`, `spring-boot-starter-actuator` |
| `src/main/resources/application.yml` | Sustituye a `application.properties`. Instancia `courseService` de CircuitBreaker, Retry y RateLimiter + Actuator |
| `CatalogController.java` | `@CircuitBreaker`, `@Retry`, `@RateLimiter` y métodos `fallback*` |
| `RestClientConfig.java` | `RestTemplate` con timeouts (conexión 1 s, lectura 2 s) |

## Patrones aplicados

| Endpoint | Anotaciones | Fallback |
|---|---|---|
| `GET /` | `@CircuitBreaker` | `fallbackGetCatalogHome` |
| `GET /catalog` | `@Retry` (3 intentos, 500 ms) + `@CircuitBreaker` | `fallbackGetCatalog` (en `@Retry`, la más externa) |
| `GET /firstcourse` | `@CircuitBreaker` + `@RateLimiter` (5 por 10 s) | `fallbackGetSpecificCourse` |

Orden de aspectos de Resilience4j: `Retry ( CircuitBreaker ( RateLimiter ( ... ) ) )`.
`@Retry` ignora `CallNotPermittedException` (circuito OPEN) y `@CircuitBreaker` ignora `RequestNotPermitted` (RateLimiter).

## Monitoreo

`/actuator/health`, `/actuator/circuitbreakers`, `/actuator/circuitbreakerevents`, `/actuator/retryevents`.

## Ejecutar

Orden: `fx-discovery-server` (8761) → `fx-course-service` (8001) → `fx-catalog-service` (8002).
Scripts en `docs/scripts/` (`run-all.sh` levanta los tres servicios y `demo.sh` ejecuta la demostración):
copiar la carpeta `scripts/` junto a los tres repos clonados como `fx-discovery-server/`, `fx-course-service/` y `fx-catalog-service/`.

## Documentación de la entrega (`docs/`)

- `Entrega_ServiceDiscovery_Resilience4j.pdf`: informe completo.
- `Presentacion_ServiceDiscovery_Resilience4j.pdf`: diapositivas.
- `evidencia/`: capturas y salidas reales de la demostración.
