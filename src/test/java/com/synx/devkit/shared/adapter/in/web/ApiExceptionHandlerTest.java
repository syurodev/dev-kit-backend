package com.synx.devkit.shared.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.synx.devkit.shared.error.ConflictException;
import com.synx.devkit.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void notFoundReturns404() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-404");

        var response = handler.notFound(new NotFoundException("device missing"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("not_found", response.getBody().code());
        assertEquals("Resource was not found", response.getBody().message());
    }

    @Test
    void conflictReturns409() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-409");

        var response = handler.conflict(new ConflictException("last active device"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("conflict", response.getBody().code());
        assertEquals("Request conflicts with current state", response.getBody().message());
    }
}
