package com.transit.graphbased_v2.service.impl;

import com.transit.graphbased_v2.controller.dto.ApplicationDTO;
import com.transit.graphbased_v2.domain.graph.nodes.ApplicationClazz;
import com.transit.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.transit.graphbased_v2.exceptions.BadRequestException;
import com.transit.graphbased_v2.exceptions.NodeIdExistsException;
import com.transit.graphbased_v2.exceptions.NodeNotFoundException;
import com.transit.graphbased_v2.performacelogging.LogExecutionTime;
import com.transit.graphbased_v2.repository.*;
import com.transit.graphbased_v2.service.ApplicationService;
import com.transit.graphbased_v2.service.helper.UpdateRightsRecursive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.Validator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApplicationServiceBean implements ApplicationService {

    @Autowired
    private Validator validator;

    @Autowired
    private EntityClazzRepository entityClazzRepository;
    @Autowired
    private ObjectClazzRepository objectClazzRepository;
    @Autowired
    private IdentityClazzRepository identityClazzRepository;
    @Autowired
    private ObjectAttributeClazzRepository objectAttributeClazzRepository;

    @Autowired
    private EntityConnectionRepository entityConnectionRepository;

    @Autowired
    private RelationshipConnectionRepository relationshipConnectionRepository;


    @Autowired
    private AssigmentRepository assigmentRepository;

    @Autowired
    private RightsConnectionRepository rightsConnectionRepository;

    @Autowired
    private RightsRepository rightsRepository;

    @Autowired
    private UpdateRightsRecursive updateRecursive;
    @Autowired
    private ApplicationClazzRepository applicationClazzRepository;
    @Autowired
    private HelpersRepository helpersRepository;

    @LogExecutionTime
    @Override
    public ApplicationDTO createApplication(ApplicationClazz dto) throws BadRequestException {

        if (applicationClazzRepository.existsById(dto.getId())) {
            throw new NodeIdExistsException(dto.getId());
        }

        var existingANode = helpersRepository.getApplicationByIdentityAndName(dto.getIdentityId(), dto.getName());

        if (existingANode.isPresent()) {
            throw new BadRequestException("Application with name '" + dto.getName() + "' already exists. Id: " + existingANode.get().getId());
        }

        var oNode = applicationClazzRepository.save(dto);

        return new ApplicationDTO(oNode.getId(), oNode.getName(), oNode.getIdentityId());
    }

    @Override
    public Optional<ApplicationDTO> getApplication(UUID applicationId) {
        return applicationClazzRepository.findById(applicationId).map(aNode -> new ApplicationDTO(aNode.getId(), aNode.getName(), aNode.getIdentityId()));
    }


    @Override
    public Optional<List<ApplicationDTO>> getApplicationsByIdentity(UUID identityId) {
        var applicationNodes = helpersRepository.getApplicationsByIdentity(identityId);

        if (applicationNodes.isEmpty()) {
            return Optional.empty();
        }

        List<ApplicationDTO> response = applicationNodes.get().stream()
                .map(app -> new ApplicationDTO(app.getId(), app.getName(), app.getIdentityId()))
                .toList();

        return Optional.of(response);
    }


    @Override
    public Optional<List<ApplicationDTO>> getApplications() {
        var applicationNodes = helpersRepository.getApplications();

        if (applicationNodes.isEmpty()) {
            return Optional.empty();
        }

        List<ApplicationDTO> response = applicationNodes.get().stream()
                .map(app -> new ApplicationDTO(app.getId(), app.getName(), app.getIdentityId()))
                .toList();

        return Optional.of(response);
    }


    @Override
    public void verifyApplicationExists(UUID applicationId) {
        var opt = getApplication(applicationId);
        if (opt.isEmpty()) {
            throw new BadRequestException("Application with ID " + applicationId + " does not exist.");
        }
    }

    @Override
    public Optional<ApplicationDTO> getApplicationByOANode(ObjectAttributeExtendedClazz oaNode) {
        Optional<ApplicationClazz> applicationOpt = helpersRepository.getApplicationByOANode(oaNode);

        if (applicationOpt.isEmpty()) {
            throw new RuntimeException("No Application node found for OA");
        }

        ApplicationClazz applicationNode = applicationOpt.get();

        ApplicationDTO response = new ApplicationDTO(
                applicationNode.getId(),
                applicationNode.getName(),
                applicationNode.getIdentityId()
        );

        return Optional.of(response);
    }


    @Override
    public ApplicationDTO patchApplication(UUID applicationId, ApplicationDTO dto) {


        if (!applicationClazzRepository.existsById(applicationId)) {
            throw new NodeNotFoundException(applicationId);
        }


        var existingANode = helpersRepository.getApplicationByIdentityAndName(dto.getIdentityId(), dto.getApplicationName());


        if (existingANode.isPresent() && !existingANode.get().getId().equals(applicationId)) {
            throw new BadRequestException("Application with name '" + dto.getApplicationName() + "' already exists. Id: " + existingANode.get().getId());
        }

        var aNode = applicationClazzRepository.findById(applicationId).get();

        //new version
        aNode.setName(dto.getApplicationName());
        applicationClazzRepository.save(aNode);

        return new ApplicationDTO(aNode.getId(), aNode.getName(), aNode.getIdentityId());
    }


}
