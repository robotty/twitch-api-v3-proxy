package de.zwb3.apiproxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/error")
public class SimpleErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    @Autowired
    public SimpleErrorController(ErrorAttributes errorAttributes) {
        Assert.notNull(errorAttributes, "ErrorAttributes must not be null");
        this.errorAttributes = errorAttributes;
    }

    @Override
    public String getErrorPath() {
        return "/error";
    }

    @RequestMapping
    public ErrorStatus error(WebRequest aRequest, HttpServletRequest servletRequest) {
        boolean includeStackTrace = false;
        Map<String, Object> body = errorAttributes.getErrorAttributes(aRequest, includeStackTrace);

        /*
         * errorAttributes (non-trace mode) has these mappings:
         * "timestamp" -> "Sun Dec 30 22:03:29 CET 2018"
         * "status" -> "404"
         * "error" -> "Not Found"
         * "message" -> "Username asdf at segment :channel (#2) could not be translated: user not found"
         * "path" -> "/kraken/streams/asdf"
         * See documentation of DefaultErrorAttributes
         */

        return new ErrorStatus((String) body.get("error"), // e.g. Not Found
                (Integer) body.get("status"), // e.g. 404
                (String) body.get("message")); // e.g. Username asdf at segment :channel (#2) could not be translated: user not found

    }
}