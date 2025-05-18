package com.transit.graphbased_v2.exceptions;

import java.util.UUID;

public class NodeIdExistsException extends RuntimeException {
    public NodeIdExistsException(UUID id) {
        super("ID '" + id + "' already exists");
    }
}
