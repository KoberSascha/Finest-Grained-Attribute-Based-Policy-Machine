package com.datatrust.graphbased_v2.service;

import com.datatrust.graphbased_v2.controller.dto.ApplicationDTO;
import com.datatrust.graphbased_v2.domain.graph.nodes.ApplicationClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationService {

    public ApplicationDTO createApplication(ApplicationClazz dto) throws BadRequestException;

    public Optional<ApplicationDTO> getApplication(UUID id);

    public void verifyApplicationExists(UUID id);

    public Optional<List<ApplicationDTO>> getApplicationsByIdentity(UUID identityId);

    public Optional<List<ApplicationDTO>> getApplications();

    public Optional<ApplicationDTO> getApplicationByOANode(ObjectAttributeExtendedClazz oaNode);

    public ApplicationDTO patchApplication(UUID applicationId, ApplicationDTO dto) throws BadRequestException;

    public long deleteApplication(UUID objectId, UUID identityId) throws BadRequestException;


   /*
    public Optional<ApplicationDTO> getObject(UUID objectId);


*/
}
