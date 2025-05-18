package com.transit.graphbased_v2.exceptions;

public class BadRequestException extends RuntimeException {
	
	public BadRequestException(String text) {
		super(text);
	}
}
