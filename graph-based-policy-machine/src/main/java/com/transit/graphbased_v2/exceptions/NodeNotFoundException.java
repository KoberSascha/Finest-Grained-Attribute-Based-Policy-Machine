package com.transit.graphbased_v2.exceptions;


import java.util.UUID;

public class NodeNotFoundException extends RuntimeException {
    public NodeNotFoundException(UUID id) {
        super(String.format("ID %s could not be found", id));
    }

    public NodeNotFoundException(String nodeName) {
        super(String.format("%s", nodeName));
    }
}
