package au.chipomho.showcase.fleet.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

final class Problems {

    private Problems() {
    }

    static ErrorResponseException badRequest(String detail) {
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    static ErrorResponseException notFound(String detail) {
        return problem(HttpStatus.NOT_FOUND, detail);
    }

    private static ErrorResponseException problem(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
