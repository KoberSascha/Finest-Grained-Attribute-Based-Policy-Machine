package com.transit.graphbased_v2.exceptions.handler;

import com.transit.graphbased_v2.exceptions.*;
import com.transit.graphbased_v2.exceptions.response.ErrorResponse;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Objects;


@RestControllerAdvice

public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
	
	public static final String TRACE = "trace";
	
	
	private boolean printStackTrace;
	
	@ExceptionHandler(NodeIdExistsException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<Object> handleExists(NodeIdExistsException ex,
	                                           WebRequest request) {
		return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
	}
	
	private ResponseEntity<Object> buildErrorResponse(Exception exception, HttpStatus httpStatus, WebRequest request) {
		return buildErrorResponse(exception, exception.getMessage(), httpStatus, request);
	}
	
	private ResponseEntity<Object> buildErrorResponse(Exception exception, String message, HttpStatus httpStatus, WebRequest request) {
		ErrorResponse errorResponse = new ErrorResponse(httpStatus.value(), message);
		if (printStackTrace && isTraceOn(request)) {
			errorResponse.setStackTrace(ExceptionUtils.getStackTrace(exception));
		}
		return ResponseEntity.status(httpStatus).body(errorResponse);
	}
	
	private boolean isTraceOn(WebRequest request) {
		String[] value = request.getParameterValues(TRACE);
		return Objects.nonNull(value)
				&& value.length > 0
				&& value[0].contentEquals("true");
	}
	
	@ExceptionHandler(NodeNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ResponseEntity<Object> handleNotExists(NodeNotFoundException ex,
	                                              WebRequest request) {
		return buildErrorResponse(ex, HttpStatus.NOT_FOUND, request);
	}
	
	@ExceptionHandler(BadRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<Object> handleNotBadRequest(BadRequestException ex,
	                                                  WebRequest request) {
		return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(ValidationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<Object> handleValidation(ValidationException ex,
	                                               WebRequest request) {
		return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(ForbiddenException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public ResponseEntity<Object> handleForbidden(ValidationException ex,
	                                              WebRequest request) {
		return buildErrorResponse(ex, HttpStatus.FORBIDDEN, request);
	}
	
}