package com.transit.graphbased_v2.service;

import com.transit.graphbased_v2.domain.graph.nodes.IdentityClazz;
import com.transit.graphbased_v2.exceptions.NodeIdExistsException;
import com.transit.graphbased_v2.exceptions.NodeNotFoundException;

import java.util.Optional;
import java.util.UUID;

public interface IdentityService {


    public IdentityClazz createIdentity(IdentityClazz userAttributeClazz) throws NodeIdExistsException;

    public Optional<IdentityClazz> getIdentity(UUID id);

    public boolean deleteIdentity(UUID id) throws NodeNotFoundException;
}
