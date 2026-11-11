package com.futurex.services.FutureXCourseApp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Guía 3: el SDK de OpenTelemetry exporta los TRES tipos de telemetría (trazas, métricas y logs)
 * por OTLP gRPC al Colector, que las reparte a Jaeger, Prometheus y Elasticsearch.
 */
@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry(@Value("${otel.service.name}") String serviceName,
                                       @Value("${otel.exporter.otlp.endpoint}") String endpoint) {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put("service.name", serviceName)
                        .put("deployment.environment", "taller")
                        .build()));

        // --- Trazas ---
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint).setTimeout(Duration.ofSeconds(5)).build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(Duration.ofSeconds(1)).build())
                .setResource(resource)
                .build();

        // --- Métricas (SDK de OTel; las de Micrometer salen por micrometer-registry-otlp) ---
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(endpoint).build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofSeconds(10)).build())
                .setResource(resource)
                .build();

        // --- Logs ---
        OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
                .setEndpoint(endpoint).build();
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter)
                        .setScheduleDelay(Duration.ofSeconds(1)).build())
                .setResource(resource)
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setLoggerProvider(loggerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        // Conecta el appender de Logback (logback-spring.xml) con este SDK
        OpenTelemetryAppender.install(sdk);
        // Libera recursos al apagar la aplicación (envía lo pendiente)
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
        return sdk;
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.futurex.services.FutureXCourseApp", "0.0.1");
    }
}
