package com.datatrust.graphbased_v2.service.impl;

import com.datatrust.graphbased_v2.controller.dto.*;
import com.datatrust.graphbased_v2.domain.graph.nodes.ClazzType;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import com.datatrust.graphbased_v2.exceptions.ForbiddenException;
import com.datatrust.graphbased_v2.exceptions.NodeNotFoundException;
import com.datatrust.graphbased_v2.performacelogging.LogExecutionTime;
import com.datatrust.graphbased_v2.repository.*;
import com.datatrust.graphbased_v2.service.AccessService;
import com.datatrust.graphbased_v2.service.helper.AccessTransferComponentHelper;
import com.datatrust.graphbased_v2.service.helper.AccessValidator;
import com.datatrust.graphbased_v2.service.helper.OADeserializer;
import com.datatrust.graphbased_v2.service.helper.OaDeletionService;
import com.datatrust.graphbased_v2.service.helper.UpdateRightsRecursive;
import com.datatrust.graphbased_v2.transferobjects.AccessTransferComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AccessServiceBean implements AccessService {

    @Autowired
    private RightsRepository rightsRepository;

    @Autowired
    private IdentityClazzRepository identityClazzRepository;

    @Autowired
    private ObjectClazzRepository objectClazzRepository;

    @Autowired
    private EntityConnectionRepository entityConnectionRepository;

    @Autowired
    private EntityClazzRepository entityClazzRepository;


    @Autowired
    private AccessValidator accessValidator;

    @Autowired
    private AccessTransferComponentHelper accessTransferComponentHelper;

    @Autowired
    private UpdateRightsRecursive updateRecursive;

    @Autowired
    private OaDeletionService oaDeletionService;


    @Autowired
    private OADeserializer oADeserializer;

    @LogExecutionTime
    @Override
    public Optional<AccessTransferComponent> getAccess(UUID applicationId, UUID oId, UUID identityId, UUID requestedById) throws NodeNotFoundException {


        var rights = rightsRepository.getRightsWithDigitAccess(applicationId, identityId, requestedById, oId);

        if (rights.isEmpty()) {
            throw new NodeNotFoundException(oId);
        }

        return rights.map(entry -> accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId));

    }


    @Override
    public Optional<AllAccessResponseDTO> getAllAccessForObject(UUID applicationId, UUID oId, UUID requestedById) {

        var rightsOpt = rightsRepository.getAllRightsForObjectWithDigitAccess(applicationId, requestedById, oId);

        if (rightsOpt.isEmpty() || rightsOpt.get().isEmpty()) {
            return Optional.empty();
        }

        var rightsList = rightsOpt.get();

        // for convenience: all identities that appear
        List<UUID> allIdentityIds = rightsList.stream()
                .map(IdentityObjectRightsDTO::getIdentityId)
                .distinct()
                .toList();


        List<AccessEntryDTO> accessEntries = rightsList.stream().map(r -> {
            OAPropertiesDTO props = new OAPropertiesDTO(
                    r.getReadProperties(),
                    r.getWriteProperties(),
                    r.getShareReadProperties(),
                    r.getShareWriteProperties(),
                    r.getDigitsAccess() != null ? r.getDigitsAccess() : new HashSet<>()
            );


            AccessEntryDTO entry = new AccessEntryDTO();
            entry.setIdentityId(r.getIdentityId());
            entry.setSharedFromIdentityId(r.getSharedFromIdentityId());
            entry.setSharedToIdentityIds(r.getSharedToIdentityIds().stream().toList());


            entry.setSharedToIdentityIds(
                    r.getSharedToIdentityIds() == null
                            ? List.of()
                            : r.getSharedToIdentityIds().stream().toList()
            );


            entry.setIdentityProperties(props);
            return entry;
        }).toList();

        String entityClass = rightsList.stream()
                .map(IdentityObjectRightsDTO::getEntityClass)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        AllAccessResponseDTO response = new AllAccessResponseDTO();
        response.setObjectId(oId);
        response.setObjectEntityClass(entityClass);
        response.setAccess(accessEntries);

        return Optional.of(response);
    }


    @Override
    public List<AccessTransferComponent> getAccessList(UUID applicationId, Set<UUID> objectIds, UUID identityId, UUID requestedById) {
        return rightsRepository.getRightsListOld(applicationId, objectIds, identityId, requestedById).stream().map(entry -> accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId)).collect(Collectors.toList());
    }

    @Override
    public List<AccessTransferComponent> getAccessClazz(UUID applicationId, String entityClazz, UUID requestedById, boolean createdByMyOwn, UUID identityId, Integer pagesize) {

        return rightsRepository.getRightsClass(applicationId, entityClazz, requestedById, createdByMyOwn, identityId, pagesize).stream().map(entry -> {

            if (identityId == null) {
                return accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), requestedById);
            } else {
                return accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), identityId);
            }
        }).collect(Collectors.toList());
    }


    @LogExecutionTime
    @Override
    public Optional<AccessTransferComponent> createConnection(UUID applicationId, OAPropertiesDTO request, UUID objectId, UUID identityId, UUID requestedById) {
        return replaceLogicalGrant(applicationId, request, objectId, identityId, requestedById, false);
    }

    @Override
    public Optional<AccessTransferComponent> updateConnection(UUID applicationId, OAPropertiesDTO request, UUID objectId, UUID identityId, UUID requestedById) {
        return replaceLogicalGrant(applicationId, request, objectId, identityId, requestedById, true);
    }

    private Optional<AccessTransferComponent> replaceLogicalGrant(UUID applicationId, OAPropertiesDTO request, UUID objectId,
                                                                 UUID recipientId, UUID giverId, boolean update) {
        validateRequest(request, recipientId, giverId);
        var context = rightsRepository.getGrantContext(applicationId, objectId, giverId, recipientId);
        if (!context.objectExists()) throw new NodeNotFoundException("Object not exists");
        if (!context.giverExists()) throw new NodeNotFoundException("Identity " + giverId + " not exists.");
        if (!context.recipientExists()) throw new NodeNotFoundException("Identity " + recipientId + " not exists.");
        if (context.sourceIds().isEmpty()) throw new BadRequestException("No Access");
        if (update && context.controlledGrants().isEmpty())
            throw new BadRequestException("Cannot find existing access data for this identity.");
        if (!update && !context.controlledGrants().isEmpty())
            throw new BadRequestException("Rights already exists--> Have to update not create.");

        Map<UUID, ObjectAttributeClazz> sources = new LinkedHashMap<>();
        for (var id : context.sourceIds()) sources.put(id, rightsRepository.findOaByIdWithDigitAccess(id)
                .orElseThrow(() -> new NodeNotFoundException("Cannot find parent access")));
        Map<UUID, ObjectAttributeClazz> replacements = new LinkedHashMap<>();
        Map<UUID, ObjectAttributeClazz> additions = new LinkedHashMap<>();
        Set<UUID> representedSources = new HashSet<>();
        Set<UUID> revokedRoots = new LinkedHashSet<>();
        for (var grant : context.controlledGrants()) {
            if (grant.parentIds().size() != 1)
                throw new BadRequestException("An access grant must have exactly one source.");
            var parentId = grant.parentIds().iterator().next();
            if (!sources.containsKey(parentId)) {
                revokedRoots.add(grant.id());
                continue;
            }
            var node = rightsRepository.findOaByIdWithDigitAccess(grant.id())
                    .orElseThrow(() -> new NodeNotFoundException("Cannot find access to update"));
            accessValidator.generateValidatedOANode(node, request, oADeserializer.parseOAtoOAExtended(sources.get(parentId)));
            replacements.put(node.getId(), node);
            representedSources.add(parentId);
        }
        // Keep a distinct child for every actual parent. Unioning parent fields into one child
        // would lose provenance and let an unrelated contributor's revocation remove/retain rights.
        for (var entry : sources.entrySet()) {
            if (representedSources.contains(entry.getKey())) continue;
            var node = new ObjectAttributeClazz();
            node.setId(UUID.randomUUID());
            node.setName("OA#" + node.getId() + "Identity#" + recipientId + "#" + objectId);
            node.setType(ClazzType.OA);
            node.setEntityClass("OA");
            accessValidator.generateValidatedOANode(node, request, oADeserializer.parseOAtoOAExtended(entry.getValue()));
            additions.put(entry.getKey(), node);
        }
        // All requests and source structures have been checked before the first mutation.
        for (var node : replacements.values())
            if (rightsRepository.updateOa(node).isEmpty()) throw new NodeNotFoundException("Cannot update access");
        for (var entry : additions.entrySet())
            if (rightsRepository.createAccess(applicationId, entry.getValue(), entry.getKey(), recipientId, giverId, objectId).isEmpty())
                throw new NodeNotFoundException("Cannot create access");
        if (!revokedRoots.isEmpty()) oaDeletionService.deleteSubtrees(revokedRoots);

        // A giver source may itself descend from an updated grant (identity cycles with a DAG
        // of OAs). Propagate only after ALL replacements/additions, so no stale snapshot is
        // persisted later than its ancestor's restriction.
        Set<UUID> changedRoots = new LinkedHashSet<>(replacements.keySet());
        additions.values().forEach(node -> changedRoots.add(node.getId()));
        for (var root : changedRoots) updateRecursive.updateRecursive(root);
        return rightsRepository.getRightsWithDigitAccess(applicationId, recipientId, giverId, objectId)
                .map(entry -> accessTransferComponentHelper.getAccessTransferComponent(entry.getObjectAttributeClazz(), entry.getOId(), entry.getOEntityClazz(), recipientId));
    }

    @Override
    public boolean deleteConnectionRecursive(UUID applicationId, UUID objectId, UUID recipientId, UUID giverId) {
        if (giverId.equals(recipientId)) {
            var received = rightsRepository.findReceivedGrantIds(applicationId, objectId, recipientId);
            if (received.isEmpty()) return false;
            oaDeletionService.deleteSubtrees(received);
            return true;
        }
        var context = rightsRepository.getGrantContext(applicationId, objectId, giverId, recipientId);
        Set<UUID> roots = context.controlledGrants().stream().map(RightsRepository.ControlledGrant::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roots.isEmpty()) return false;
        oaDeletionService.deleteSubtrees(roots);
        return true;
    }

    public void deleteOaNodes(HashSet<UUID> oaNodes) {
        oaDeletionService.deleteSubtrees(oaNodes);
    }

    private void validateRequest(OAPropertiesDTO request, UUID recipientId, UUID giverId) {
        if (giverId.equals(recipientId)) throw new BadRequestException("Cannot update access for own identity.");
        int read = size(request.getReadProperties()), write = size(request.getWriteProperties());
        if (read < write) throw new BadRequestException("Cannot have more write Properties as read Properties.");
        if (read < size(request.getShareReadProperties())) throw new BadRequestException("Cannot have more sharing read Properties as read Properties.");
        if (write < size(request.getShareWriteProperties())) throw new BadRequestException("Cannot have more sharing write Properties as write Properties.");
    }

    private int size(Set<String> fields) { return fields == null ? 0 : fields.size(); }
}
