package au.chipomho.showcase.fleet.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The problem detail (RFC 9457) every error response carries, in exactly the four fields the API
 * contract lists.
 */
public record Problem(String type, String title, int status, String detail) {

    static Problem of(HttpStatusCode status, String detail) {
        return new Problem("about:blank", HttpStatus.valueOf(status.value()).getReasonPhrase(), status.value(), detail);
    }
}
