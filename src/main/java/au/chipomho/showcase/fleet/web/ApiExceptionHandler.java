package au.chipomho.showcase.fleet.web;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Spring decides the status and detail for its own exceptions and for ours, which are all
 * {@link org.springframework.web.ErrorResponse}s. This only changes how the result is written: as
 * a {@link Problem} rather than Spring's {@link ProblemDetail}.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode status, WebRequest request) {
        var detail = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : HttpStatus.valueOf(status.value()).getReasonPhrase();
        return problem(status, detail, headers);
    }

    @ExceptionHandler
    ResponseEntity<Object> unexpected(Exception exception) {
        LOG.error("Request failed", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed", HttpHeaders.EMPTY);
    }

    private static ResponseEntity<Object> problem(HttpStatusCode status, String detail, HttpHeaders headers) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problem.of(status, detail));
    }
}
