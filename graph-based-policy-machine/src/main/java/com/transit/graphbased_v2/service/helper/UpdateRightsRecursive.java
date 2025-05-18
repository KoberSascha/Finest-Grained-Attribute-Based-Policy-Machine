package com.transit.graphbased_v2.service.helper;

import com.transit.graphbased_v2.repository.ObjectAttributeClazzRepository;
import com.transit.graphbased_v2.repository.RightsConnectionRepository;
import com.transit.graphbased_v2.repository.RightsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UpdateRightsRecursive {

    @Autowired
    private RightsConnectionRepository rightsConnectionRepository;


    @Autowired
    private ObjectAttributeClazzRepository objectAttributeClazzRepository;

    @Autowired
    private RightsRepository rightsRepository;
    @Autowired
    private AccessValidator accessValidator;
    @Autowired
    private OADeserializer oADeserializer;


    public boolean updateRecursive(UUID startOANode) {

        var parent = rightsRepository.findOaByIdWithDigitAccess(startOANode).get();
        var parentId = parent.getId();

        rightsConnectionRepository.getIncomingRelationships(parentId).forEach(childId -> {

            var child = rightsRepository.findOaByIdWithDigitAccess(childId.getSourceID()).get();

            child.setReadProperties(accessValidator.validateAccessPropertiesAgainstParentProperties(child.getReadProperties(), parent.getReadProperties()));
            child.setWriteProperties(accessValidator.validateAccessPropertiesAgainstParentProperties(child.getWriteProperties(), parent.getWriteProperties()));
            child.setShareReadProperties(accessValidator.validateAccessPropertiesAgainstParentProperties(child.getShareReadProperties(), parent.getShareReadProperties()));
            child.setShareWriteProperties(accessValidator.validateAccessPropertiesAgainstParentProperties(child.getShareWriteProperties(), parent.getShareWriteProperties()));

            //validate child digitaccess with parent digitAccess
            //child.setDigitsAccess(validateDigitAccess(child.getDigitsAccess(), parent.getDigitsAccess()));


            rightsRepository.updateOa(child);

            //update the properties through digitAccess
            // child.setDigitsAccess(accessValidator.validateRightsPropertiesDigitAccessAgainstParentOa(child.getDigitsAccess(), parent.getDigitsAccess()));
            var validateRightsPropertiesDigitAccess = accessValidator.validateRightsPropertiesDigitAccessAgainstParentOa(child.getDigitsAccess(), parent.getDigitsAccess(), oADeserializer.parseOAtoOAExtended(child));

            //Now delete all DA-Nodes and add and connect the new validated DA-Nodes
            rightsRepository.createDigitAccess(validateRightsPropertiesDigitAccess, child.getId());


            rightsConnectionRepository.getIncomingRelationships(child.getId()).forEach(childChildId -> updateRecursive(childChildId.getSourceID()));
        });

        return true;
    }


}
