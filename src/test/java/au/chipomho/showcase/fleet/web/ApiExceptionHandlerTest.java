package au.chipomho.showcase.fleet.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The paths the HTTP tests can't reach: a response with no problem body or no detail, and a
 * failure nobody planned for.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void fallsBackToTheStatusWhenThereIsNoDetail() {
        var noBody = handler.createResponseEntity(null, new HttpHeaders(), HttpStatus.CONFLICT, null);
        var noDetail = handler.createResponseEntity(ProblemDetail.forStatus(HttpStatus.CONFLICT), new HttpHeaders(),
                HttpStatus.CONFLICT, null);

        assertThat(noBody.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(noBody.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(noBody.getBody()).isEqualTo(new Problem("about:blank", "Conflict", 409, "Conflict"));
        assertThat(noDetail.getBody()).isEqualTo(noBody.getBody());
    }

    @Test
    void turnsAnUnexpectedFailureIntoA500AndLogsIt() {
        var logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        var log = new ListAppender<ILoggingEvent>();
        log.start();
        logger.addAppender(log);
        logger.setAdditive(false);
        try {
            var failure = new IllegalStateException("Redis is down");
            var response = handler.unexpected(failure);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isEqualTo(new Problem("about:blank", "Internal Server Error", 500,
                    "The request could not be completed"));
            assertThat(log.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("Redis is down");
            });
        } finally {
            logger.detachAppender(log);
            logger.setAdditive(true);
        }
    }
}
