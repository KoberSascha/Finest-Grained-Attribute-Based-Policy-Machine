package com.datatrust.graphbased_v2.controller.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A scoped, allowlisted excerpt of persisted policy nodes, never a database dump. */
public record PolicyStorageDTO(UUID applicationId, UUID objectId, UUID identityId, UUID requestedById,
                               String storage, Instant checkedAtUtc, List<Node> nodes, List<Edge> edges) {
    public record Node(String id, List<String> labels, Map<String, Object> properties) { }
    public record Edge(String source, String target, String type, Map<String, Object> properties) { }
}
