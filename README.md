# Taller de Monitoreo y Observabilidad — FutureX

**Integrantes:** Pablo Guzmán Alarcón, Juan David Quintero, Alejandro Troya y Stick Martinez

Observabilidad completa de `fx-catalog-service` y `fx-course-service` con **OpenTelemetry Collector**:

| Señal   | Camino                                                                                 |
|---------|----------------------------------------------------------------------------------------|
| Trazas  | servicios → OTLP gRPC (4319) → Collector → Jaeger (`:16686`)                           |
| Métricas| servicios → OTLP (Micrometer, 4320) → Collector → exporter `:8889` ← Prometheus (`:9090`) → Grafana (`:3000`) |
| Logs    | servicios (Logback + OpenTelemetryAppender) → Collector → archivo → Logstash → Elasticsearch (`otel-logs-*`) → Kibana (`:5601`) |

## Ejecutar
```bash
docker compose up -d                       # Jaeger, Collector, Prometheus, Grafana, Elasticsearch, Logstash, Kibana
for s in fx-discovery-server fx-course-service fx-catalog-service; do (cd $s && sh ./mvnw -DskipTests package); done
java -jar fx-discovery-server/target/*.jar &   # esperar ~20 s
java -jar fx-course-service/target/*.jar &     # esperar ~35 s
java -jar fx-catalog-service/target/*.jar &
./scripts/trafico.sh 60                    # genera carga
```
Kibana: crear data view `otel-logs-*` (campo de tiempo `@timestamp`). Grafana: http://localhost:3000 (admin/admin), dashboard *FX Microservicios – Observabilidad*.

## Queries del taller
* ELK: `service.name: "fx-catalog-service" and log.level: "ERROR"` (o el JSON del enunciado) sobre `otel-logs-*`.
* Prometheus: `rate(catalog_courses_request_seconds_sum{endpoint="/catalog"}[15m]) / rate(catalog_courses_request_seconds_count{endpoint="/catalog"}[15m])`

Simular falla: detener `fx-course-service` y llamar a `/catalog` (circuit breaker → alerta de Grafana en *Firing*).
