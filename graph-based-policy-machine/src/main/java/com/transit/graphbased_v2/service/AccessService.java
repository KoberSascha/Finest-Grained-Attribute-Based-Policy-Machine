package com.transit.graphbased_v2.service;

import com.transit.graphbased_v2.controller.dto.OAPropertiesDTO;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.exceptions.ForbiddenException;
import com.transit.graphbased_v2.exceptions.NodeNotFoundException;
import com.transit.graphbased_v2.transferobjects.AccessTransferComponent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccessService {

    public Optional<AccessTransferComponent> updateConnection(UUID applicationId, OAPropertiesDTO oaPropertiesdto, UUID oId, UUID identityId, UUID requestedById) throws BadRequestException, ForbiddenException;

    public Optional<AccessTransferComponent> getAccess(UUID applicationId, UUID oId, UUID identityId, UUID requestedById) throws NodeNotFoundException;


    public List<AccessTransferComponent> getAccessList(UUID applicationId, Set<UUID> objectIds, UUID identityId, UUID requestedById);


    public List<AccessTransferComponent> getAccessClazz(UUID applicationId, String entityClazz, UUID requestedById, boolean createdByMyOwn, UUID identityId, Integer pagesize);


    public Optional<AccessTransferComponent> createConnection(UUID applicationId, OAPropertiesDTO oaPropertiesdto, UUID oId, UUID identityId, UUID requestedById) throws BadRequestException, ForbiddenException;

    public boolean deleteConnectionRecursive(UUID applicationId, UUID oId, UUID identityId, UUID requestedById);

}
