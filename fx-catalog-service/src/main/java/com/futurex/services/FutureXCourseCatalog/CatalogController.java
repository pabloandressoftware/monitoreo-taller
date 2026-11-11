package com.futurex.services.FutureXCourseCatalog;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class CatalogController {

    private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

    /** Nombre con el que fx-course-service se registra en Eureka (spring.application.name). */
    private static final String COURSE_SERVICE_ID = "fx-course-service";

    private final EurekaClient client;
    private final RestTemplate restTemplate;
    private final Tracer tracer;
    private final OpenTelemetry openTelemetry;
    private final MeterRegistry meterRegistry;

    // Escribe el header traceparent en la petición saliente hacia fx-course-service
    private static final TextMapSetter<HttpHeaders> SETTER = HttpHeaders::set;

    public CatalogController(EurekaClient client, RestTemplate restTemplate,
                             Tracer tracer, OpenTelemetry openTelemetry, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.client = client;
        this.restTemplate = restTemplate;
        this.tracer = tracer;
        this.openTelemetry = openTelemetry;
    }

    /** Timer (Guía 3) con la etiqueta endpoint; publica histograma para calcular percentiles. */
    private Timer timer(String name, String endpoint) {
        return Timer.builder(name).tag("endpoint", endpoint)
                .description("Tiempo de respuesta del endpoint " + endpoint)
                .publishPercentileHistogram().register(meterRegistry);
    }

    /**
     * Llama a fx-course-service dentro de un span CLIENT y propaga el contexto de la traza
     * (header traceparent) para que ambos servicios queden en una misma traza.
     */
    private <T> T tracedGet(String spanName, String url, Class<T> type) {
        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("http.method", "GET")
                .setAttribute("http.url", url)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            log.info("Llamando a fx-course-service: {}", url);
            HttpHeaders headers = new HttpHeaders();
            openTelemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), headers, SETTER);
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            // El scope del try-with-resources ya se cerró: se reactiva el span para que el log lleve traceId/spanId
            try (Scope errorScope = span.makeCurrent()) {
                log.error("Error llamando a fx-course-service ({}): {}", url, e.toString());
            }
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Service Discovery: pregunta a Eureka por una instancia de fx-course-service
     * y devuelve su URL base (termina en "/").
     */
    private String courseServiceUrl() {
        InstanceInfo instanceInfo = client.getNextServerFromEureka(COURSE_SERVICE_ID, false);
        return instanceInfo.getHomePageUrl();
    }

    @GetMapping("/")
    @CircuitBreaker(name = "courseService", fallbackMethod = "fallbackGetCatalogHome")
    public String getCatalogHome() {
        log.info("Received request for catalog home");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String courseAppMessage = tracedGet("CatalogController.getCatalogHome", courseServiceUrl(), String.class);
            return "Welcome to FutureX Course Catalog " + courseAppMessage;
        } finally {
            sample.stop(timer("catalog.home.request", "/"));
        }
    }

    // El fallback va en @Retry (la anotación más externa): si estuviera en @CircuitBreaker
    // el error quedaría absorbido y @Retry nunca llegaría a reintentar.
    @GetMapping("/catalog")
    @Retry(name = "courseService", fallbackMethod = "fallbackGetCatalog")
    @CircuitBreaker(name = "courseService")
    public String getCatalog() {
        log.info("Received request for course catalog");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String courses = tracedGet("CatalogController.getCatalog", courseServiceUrl() + "courses", String.class);
            log.info("Returning course catalog");
            return "Our courses are " + courses;
        } finally {
            sample.stop(timer("catalog.courses.request", "/catalog"));
        }
    }

    @GetMapping("/firstcourse")
    @CircuitBreaker(name = "courseService", fallbackMethod = "fallbackGetSpecificCourse")
    @RateLimiter(name = "courseService")
    public String getSpecificCourse() {
        log.info("Received request for first course");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Course course = tracedGet("CatalogController.getSpecificCourse", courseServiceUrl() + "1", Course.class);
            return "Our first course is " + course.getCoursename();
        } finally {
            sample.stop(timer("catalog.firstcourse.request", "/firstcourse"));
        }
    }

    // ---- Métodos de fallback (misma firma + el Exception como último parámetro) ----

    public String fallbackGetCatalogHome(Exception ex) {
        log.warn("fallbackGetCatalogHome: {}", ex.toString());
        return "Fallback: Welcome to FutureX Course Catalog, but the course service is not available (" + reason(ex) + ").";
    }

    public String fallbackGetCatalog(Exception ex) {
        log.warn("fallbackGetCatalog: {}", ex.toString());
        return "Fallback: Unable to retrieve course catalog (" + reason(ex) + ").";
    }

    public String fallbackGetSpecificCourse(Exception ex) {
        log.warn("fallbackGetSpecificCourse: {}", ex.toString());
        return "Fallback: Unable to retrieve the first course (" + reason(ex) + ").";
    }

    private String reason(Exception ex) {
        if (ex instanceof CallNotPermittedException) {
            return "circuit breaker is OPEN";
        }
        if (ex instanceof RequestNotPermitted) {
            return "rate limit exceeded";
        }
        return "course service unavailable";
    }
}
