package com.datatrust.graphbased_v2.service.impl;

import com.datatrust.graphbased_v2.controller.dto.ObjectDTO;
import com.datatrust.graphbased_v2.domain.graph.nodes.ClazzType;
import com.datatrust.graphbased_v2.domain.graph.nodes.EntityClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectAttributeExtendedClazz;
import com.datatrust.graphbased_v2.domain.graph.nodes.ObjectClazz;
import com.datatrust.graphbased_v2.domain.graph.relationships.EntityConnection;
import com.datatrust.graphbased_v2.exceptions.BadRequestException;
import com.datatrust.graphbased_v2.repository.EntityClazzRepository;
import com.datatrust.graphbased_v2.repository.EntityConnectionRepository;
import com.datatrust.graphbased_v2.repository.ObjectAttributeClazzRepository;
import com.datatrust.graphbased_v2.repository.ObjectClazzRepository;
import com.datatrust.graphbased_v2.repository.RightsConnectionRepository;
import com.datatrust.graphbased_v2.repository.RightsRepository;
import com.datatrust.graphbased_v2.service.helper.OaDeletionService;
import com.datatrust.graphbased_v2.service.helper.UpdateRightsRecursive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObjectServiceBeanTest {

    @Mock private EntityClazzRepository entities;
    @Mock private ObjectClazzRepository objects;
    @Mock private ObjectAttributeClazzRepository objectAttributes;
    @Mock private EntityConnectionRepository entityConnections;
    @Mock private RightsConnectionRepository rightsConnections;
    @Mock private RightsRepository rights;
    @Mock private UpdateRightsRecursive updateRecursive;
    @Mock private OaDeletionService deletion;
    @InjectMocks private ObjectServiceBean service;

    private final UUID applicationId = UUID.randomUUID();
    private final UUID identityId = UUID.randomUUID();
    private final UUID objectId = UUID.randomUUID();
    private final UUID rootOaId = UUID.randomUUID();
    private final EntityClazz originalEntity = entity("Original");
    private final EntityConnection originalConnection = new EntityConnection(originalEntity.getId(), objectId);

    @Test
    void classChangeCreatesAnApplicationConnectedEntityWhenClassIsNew() {
        prepareUpdate();
        when(entities.findByApplicationIdAndEntityClass(applicationId, "Target")).thenReturn(Optional.empty());
        when(rights.createEntity(eq(applicationId), any(EntityClazz.class))).thenReturn(true);

        var result = service.updateObject(applicationId, dto("Target"));

        var target = ArgumentCaptor.forClass(EntityClazz.class);
        verify(rights).createEntity(eq(applicationId), target.capture());
        assertNotNull(target.getValue().getId());
        assertEquals("Target", target.getValue().getEntityClass());
        assertEquals(ClazzType.E, target.getValue().getType());
        assertEquals("Target", target.getValue().getName());
        verifyClassChangeOrder(target.getValue().getId());
        verify(entities, never()).save(any(EntityClazz.class));
        assertEquals("Target", result.getObjectEntityClass());
        assertEquals("Target#" + objectId, result.getName());
    }

    @Test
    void classChangeDoesNotReuseAnEntityThatOnlyExistsInAnotherApplication() {
        prepareUpdate();
        var otherApplicationId = UUID.randomUUID();
        var foreignEntity = entity("Target");
        when(entities.findByApplicationIdAndEntityClass(any(UUID.class), eq("Target")))
                .thenAnswer(call -> otherApplicationId.equals(call.getArgument(0))
                        ? Optional.of(foreignEntity) : Optional.empty());
        when(rights.createEntity(eq(applicationId), any(EntityClazz.class))).thenReturn(true);

        service.updateObject(applicationId, dto("Target"));

        var target = ArgumentCaptor.forClass(EntityClazz.class);
        verify(rights).createEntity(eq(applicationId), target.capture());
        assertNotEquals(foreignEntity.getId(), target.getValue().getId());
        verify(entities).findByApplicationIdAndEntityClass(applicationId, "Target");
        verify(entities, never()).findAllByEntityClass(anyString());
        verify(entities, never()).existsEntityClazzByEntityClass(anyString());
        verifyClassChangeOrder(target.getValue().getId());
    }

    @Test
    void classChangeReusesAnEntityWithinTheSameApplication() {
        prepareUpdate();
        var target = entity("Target");
        when(entities.findByApplicationIdAndEntityClass(applicationId, "Target")).thenReturn(Optional.of(target));

        service.updateObject(applicationId, dto("Target"));

        verify(rights, never()).createEntity(any(), any());
        verifyClassChangeOrder(target.getId());
    }

    @Test
    void sameClassUpdatesRightsWithoutChangingEntityConnections() {
        var oa = prepareUpdate();

        var result = service.updateObject(applicationId, dto("Original"));

        verify(entities, never()).findByApplicationIdAndEntityClass(any(), any());
        verify(rights, never()).createEntity(any(), any());
        verify(entityConnections, never()).createRelationship(any());
        verify(entityConnections, never()).deleteRelationship(any());
        verify(objects, never()).save(any());
        assertEquals(Set.of("name"), oa.getReadProperties());
        assertEquals(Set.of("name"), oa.getWriteProperties());
        assertEquals(Set.of("name"), oa.getShareReadProperties());
        assertEquals(Set.of("name"), oa.getShareWriteProperties());
        verify(objectAttributes).save(oa);
        verify(updateRecursive).updateRecursive(rootOaId);
        assertEquals("Original#" + objectId, result.getName());
    }

    @Test
    void failedTargetConnectionPropagatesWithoutRetryingOrRemovingOriginalConnection() {
        prepareUpdate();
        when(entities.findByApplicationIdAndEntityClass(applicationId, "Target"))
                .thenReturn(Optional.of(entity("Target")));
        var failure = new IllegalStateException("Connection could not be created");
        when(entityConnections.createRelationship(any())).thenThrow(failure);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> service.updateObject(applicationId, dto("Target")))));

        verify(entityConnections).createRelationship(any());
        verify(entityConnections, never()).deleteRelationship(any());
        verify(objects, never()).save(any());
        verify(objectAttributes, never()).save(any());
        verifyNoInteractions(updateRecursive);
    }

    @Test
    void deleteObjectFinishesRightsSubtreeBeforeRemovingEntityConnectionsAndObjectOnce() {
        prepareOwnerRights();
        var secondConnection = new EntityConnection(UUID.randomUUID(), objectId);
        when(entityConnections.getIncomingRelationships(objectId))
                .thenReturn(List.of(originalConnection, secondConnection));

        assertTrue(service.deleteObject(applicationId, objectId, identityId));

        var order = inOrder(deletion, entityConnections, objects);
        order.verify(deletion).deleteSubtree(rootOaId);
        order.verify(entityConnections).getIncomingRelationships(objectId);
        order.verify(entityConnections).deleteRelationship(originalConnection);
        order.verify(entityConnections).deleteRelationship(secondConnection);
        order.verify(objects).deleteById(objectId);
        order.verifyNoMoreInteractions();
        verify(deletion, times(1)).deleteSubtree(rootOaId);
        verify(objects, times(1)).deleteById(objectId);
        verifyNoInteractions(entities, objectAttributes);
    }

    @Test
    void failedSubtreeDeletionLeavesTheObjectAndItsEntityConnectionsIntact() {
        prepareOwnerRights();
        var failure = new BadRequestException("Cycle detected in rights graph");
        doThrow(failure).when(deletion).deleteSubtree(rootOaId);

        assertSame(failure, assertThrows(BadRequestException.class,
                () -> service.deleteObject(applicationId, objectId, identityId)));

        verifyNoInteractions(entityConnections, objects, entities, objectAttributes);
    }

    private void prepareOwnerRights() {
        when(rights.findOwnedOaId(applicationId, objectId, identityId)).thenReturn(Optional.of(rootOaId));
        when(rightsConnections.getOutgoingRelationships(rootOaId)).thenReturn(List.of());
    }

    @Test
    void receivedGrantsCannotSubstituteForAnOwnershipRootDuringDelete() {
        when(rights.findOwnedOaId(applicationId, objectId, identityId)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.deleteObject(applicationId, objectId, identityId));
        verify(rights, never()).getRights(any(), any(), any(), any());
        verifyNoInteractions(deletion, objects, entityConnections);
    }

    @Test
    void receivedGrantsCannotSubstituteForAnOwnershipRootDuringUpdate() {
        when(rights.findOwnedOaId(applicationId, objectId, identityId)).thenReturn(Optional.empty());
        assertThrows(com.datatrust.graphbased_v2.exceptions.NodeNotFoundException.class,
                () -> service.updateObject(applicationId, dto("Target")));
        verify(rights, never()).getRights(any(), any(), any(), any());
        verifyNoInteractions(deletion, objects, entityConnections, objectAttributes);
    }

    private ObjectAttributeClazz prepareUpdate() {
        prepareOwnerRights();
        var oa = new ObjectAttributeClazz();
        oa.setId(rootOaId);
        when(objectAttributes.findById(rootOaId)).thenReturn(Optional.of(oa));
        var object = new ObjectClazz();
        object.setId(objectId);
        object.setEntityClass("Original");
        object.setName("Original#" + objectId);
        when(objects.findById(objectId)).thenReturn(Optional.of(object));
        when(entityConnections.getIncomingRelationships(objectId)).thenReturn(List.of(originalConnection));
        when(entities.findById(originalEntity.getId())).thenReturn(Optional.of(originalEntity));
        return oa;
    }

    private ObjectDTO dto(String entityClass) {
        return new ObjectDTO(objectId, entityClass, identityId, Set.of("name"));
    }

    private EntityClazz entity(String entityClass) {
        var entity = new EntityClazz();
        entity.setId(UUID.randomUUID());
        entity.setEntityClass(entityClass);
        entity.setType(ClazzType.E);
        entity.setName(entityClass);
        return entity;
    }

    private void verifyClassChangeOrder(UUID targetEntityId) {
        var order = inOrder(entityConnections, objects);
        order.verify(entityConnections).createRelationship(argThat(connection ->
                targetEntityId.equals(connection.getSourceID()) && objectId.equals(connection.getTargetID())));
        order.verify(objects).save(argThat(object -> "Target".equals(object.getEntityClass())));
        order.verify(entityConnections).deleteRelationship(originalConnection);
    }
}
