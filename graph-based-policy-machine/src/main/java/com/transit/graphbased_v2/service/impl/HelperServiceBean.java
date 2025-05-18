package com.transit.graphbased_v2.service.impl;

import com.transit.graphbased_v2.controller.dto.EntityPropertiesDTO;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.exceptions.ForbiddenException;
import com.transit.graphbased_v2.repository.*;
import com.transit.graphbased_v2.service.HelperService;
import com.transit.graphbased_v2.service.helper.AccessTransferComponentHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HelperServiceBean implements HelperService {

    @Autowired
    private RightsRepository rightsRepository;

    @Autowired
    private ObjectClazzRepository objectClazzRepository;

    @Autowired
    private ObjectAttributeClazzRepository objectAttributeClazzRepository;

    @Autowired
    private RightsConnectionRepository rightsConnectionRepository;

    @Autowired
    private EntityConnectionRepository entityConnectionRepository;

    @Autowired
    private RelationshipConnectionRepository relationshipConnectionRepository;


    @Autowired
    private AssigmentRepository assigmentRepository;

    @Autowired
    private EntityClazzRepository entityClazzRepository;


    @Autowired
    private AccessTransferComponentHelper accessTransferComponentHelper;

    @Override
    public boolean renamePropertyOfEntity(UUID applicationId, UUID requestedById, EntityPropertiesDTO entityPropertiesDTO) throws BadRequestException, ForbiddenException {

        rightsRepository.renamePropertyOfEntityByIdentity(applicationId, requestedById, entityPropertiesDTO.getEntityClass(), entityPropertiesDTO.getPropertyOldName(), entityPropertiesDTO.getPropertyNewName());
        return true;
    }

    @Override
    public boolean addPropertyOfEntity(UUID applicationId, UUID requestedById, EntityPropertiesDTO entityPropertiesDTO) throws BadRequestException, ForbiddenException {

        rightsRepository.addPropertyToEntityByIdentity(applicationId, requestedById, entityPropertiesDTO.getEntityClass(), entityPropertiesDTO.getPropertyNewName());
        return true;
    }


}
