package com.transit.graphbased_v2.exceptions;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String text) {
        super(text);
    }
}
