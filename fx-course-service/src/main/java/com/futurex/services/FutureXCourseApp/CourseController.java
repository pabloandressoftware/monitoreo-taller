package com.futurex.services.FutureXCourseApp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;

@RestController
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Tracer tracer;

    @Autowired
    private OpenTelemetry openTelemetry;

    // Lee los headers HTTP (traceparent) que envía fx-catalog-service
    private static final TextMapGetter<HttpServletRequest> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest req) {
            return java.util.Collections.list(req.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest req, String key) {
            return req == null ? null : req.getHeader(key);
        }
    };

    /** Crea un span SERVER hijo del span que viene en la petición entrante. */
    private Span startServerSpan(HttpServletRequest request, String name) {
        Context parent = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), request, GETTER);
        return tracer.spanBuilder(name)
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", request.getMethod())
                .setAttribute("http.target", request.getRequestURI())
                .startSpan();
    }

    @RequestMapping("/")
    public String getCourseAppHome(HttpServletRequest request) {
        Span span = startServerSpan(request, "CourseController.getCourseAppHome");
        try (Scope scope = span.makeCurrent()) {
            return ("Course App Home");
        } finally {
            span.end();
        }
    }

    @RequestMapping("/courses")
    public List<Course> getCourses(HttpServletRequest request) {
        Span span = startServerSpan(request, "CourseController.getCourses");
        try (Scope scope = span.makeCurrent()) {
            meterRegistry.counter("courses.accessed").increment();   // Guía 3
            List<Course> courses = courseRepository.findAll();
            span.setAttribute("courses.count", courses.size());
            log.info("Consulta de cursos: {} resultados", courses.size());
            return courses;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @RequestMapping("/{id}")
    public Course getSpecificCourse(@PathVariable("id") BigInteger id, HttpServletRequest request) {
        Span span = startServerSpan(request, "CourseController.getSpecificCourse");
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("course.id", id.longValue());
            return courseRepository.getOne(id);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @RequestMapping(method = RequestMethod.POST, value="/courses")
    public void saveCourse(@RequestBody Course course) {
        courseRepository.save(course);
    }

    @RequestMapping(method = RequestMethod.DELETE,value = "{id}")
    public void deleteCourse(@PathVariable BigInteger id) {
        courseRepository.deleteById(id);
    }


}
