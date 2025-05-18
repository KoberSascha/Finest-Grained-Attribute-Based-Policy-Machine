package com.transit.graphbased_v2.exceptions;

public class ValidationException extends RuntimeException {
	public ValidationException(String text) {
		super(text);
	}
}
