package de.zwb3.apiproxy;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadClientIDException extends IllegalArgumentException {
    public BadClientIDException() {
    }

    public BadClientIDException(String s) {
        super(s);
    }

    public BadClientIDException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadClientIDException(Throwable cause) {
        super(cause);
    }
}
