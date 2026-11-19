package com.datatrust.graphbased_v2.service;

import com.datatrust.graphbased_v2.controller.dto.ObjectDTO;
import com.datatrust.graphbased_v2.controller.dto.ObjectResponseDTO;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import com.datatrust.graphbased_v2.exceptions.ForbiddenException;

import java.util.Optional;
import java.util.UUID;

public interface ObjectService {

    public ObjectResponseDTO createObject(UUID applicationId, ObjectDTO dto) throws BadRequestException;

    ObjectResponseDTO updateObject(UUID applicationId, ObjectDTO dto) throws BadRequestException;

    public Optional<ObjectResponseDTO> getObject(UUID applicationId, UUID objectId);

    public boolean deleteObject(UUID applicationId, UUID objectId, UUID identityId) throws ForbiddenException, BadRequestException;

}
